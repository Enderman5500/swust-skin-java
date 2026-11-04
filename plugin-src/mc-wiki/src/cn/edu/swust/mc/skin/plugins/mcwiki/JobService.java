package cn.edu.swust.mc.skin.plugins.mcwiki;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 后台任务：版本导入与渲染资产更新都跑在单线程队列里。
 * 宿主没有 SSE 通道，前端通过 /admin/job?afterSeq= 轮询增量事件。
 */
final class JobService {
    record Job(String jobId, String version, String phase, long done, long total, String status, String error,
               String message, long createdAt, long updatedAt, long lastSeq) {
    }

    @FunctionalInterface
    interface Work {
        void run(String version, Control control);
    }

    private static final class Cancelled extends RuntimeException {
    }

    private final WikiStore store;
    private final Work work;
    private final ExecutorService executor;
    private final Map<String, AtomicBoolean> pauses = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> cancels = new ConcurrentHashMap<>();
    private final Set<String> removed = ConcurrentHashMap.newKeySet();

    JobService(WikiStore store, Work work) {
        this.store = store;
        this.work = work;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(4),
                Thread.ofVirtual().name("mc-wiki-job-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    void shutdown() {
        executor.shutdownNow();
    }

    synchronized Job create(String version) {
        for (Job job : jobs()) {
            if (job.version().equals(version) && !terminal(job.status())) return job;
        }
        long now = System.currentTimeMillis();
        Job job = new Job(UUID.randomUUID().toString(), version, "QUEUE", 0, 0, "PENDING", null, "已加入队列", now, now, 0);
        removed.remove(job.jobId());
        save(job);
        pauses.put(job.jobId(), new AtomicBoolean());
        cancels.put(job.jobId(), new AtomicBoolean());
        try {
            executor.execute(() -> run(job));
        } catch (RuntimeException ex) {
            fail(job, "任务队列已满，请稍后再试");
        }
        return job;
    }

    synchronized List<Job> jobs() {
        List<Job> result = new ArrayList<>();
        for (Map<String, Object> row : store.jobs()) result.add(read(row));
        result.sort(Comparator.comparingLong(Job::createdAt).reversed());
        return result;
    }

    synchronized Job get(String jobId) {
        for (Map<String, Object> row : store.jobs()) {
            if (String.valueOf(row.get("jobId")).equals(jobId)) return read(row);
        }
        return null;
    }

    List<Map<String, Object>> events(String jobId, long afterSeq) {
        return store.events(jobId, afterSeq);
    }

    synchronized Job pause(String jobId) {
        Job job = get(jobId);
        if (job == null || terminal(job.status())) return job;
        AtomicBoolean flag = pauses.get(jobId);
        if (flag != null) flag.set(true);
        Job next = update(job, job.phase(), job.done(), job.total(), "PAUSING", null, "正在等待安全点暂停");
        emit(next, "state", "正在等待安全点暂停", "INFO");
        return get(jobId);
    }

    synchronized Job resume(String jobId) {
        Job job = get(jobId);
        if (job == null || terminal(job.status())) return job;
        AtomicBoolean flag = pauses.get(jobId);
        if (flag != null) flag.set(false);
        Job next = update(job, job.phase(), job.done(), job.total(), "RUNNING", null, "正在继续任务");
        emit(next, "state", "正在继续任务", "INFO");
        return get(jobId);
    }

    synchronized Job cancel(String jobId) {
        Job job = get(jobId);
        if (job == null) return null;
        AtomicBoolean flag = cancels.get(jobId);
        if (flag != null) flag.set(true);
        if (terminal(job.status())) return job;
        Job next = update(job, job.phase(), job.done(), job.total(), "CANCELLING", null, "正在等待安全点取消");
        emit(next, "state", "正在等待安全点取消", "INFO");
        return get(jobId);
    }

    synchronized void delete(String jobId) {
        Job job = get(jobId);
        if (job == null) return;
        AtomicBoolean flag = cancels.get(jobId);
        if (flag != null) flag.set(true);
        removed.add(jobId);
        List<Map<String, Object>> remaining = new ArrayList<>();
        for (Map<String, Object> row : store.jobs()) {
            if (!String.valueOf(row.get("jobId")).equals(jobId)) remaining.add(row);
        }
        store.writeJobs(remaining);
        store.deleteEvents(jobId);
        pauses.remove(jobId);
        cancels.remove(jobId);
    }

    private void run(Job original) {
        Control control = new Control(original);
        try {
            control.setStatus("RUNNING");
            control.progress("START", 0, 1, "任务已启动");
            work.run(original.version(), control);
            control.finish("DONE", null, "任务完成");
            Job done = get(original.jobId());
            if (done != null) emit(done, "state", "任务完成", "INFO");
        } catch (Cancelled ex) {
            control.finish("CANCELLED", null, "任务已取消");
            Job cancelled = get(original.jobId());
            if (cancelled != null) emit(cancelled, "state", "任务已取消", "WARN");
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName() : ex.getMessage();
            control.finish("FAILED", message, message);
            Job failed = get(original.jobId());
            if (failed != null) emit(failed, "state", message, "ERROR");
        } finally {
            pauses.remove(original.jobId());
            cancels.remove(original.jobId());
        }
    }

    private void fail(Job job, String message) {
        Job failed = update(job, "QUEUE", 0, 0, "FAILED", message, message);
        emit(failed, "state", message, "ERROR");
    }

    private boolean terminal(String status) {
        return List.of("DONE", "FAILED", "CANCELLED").contains(status);
    }

    private synchronized Job update(Job old, String phase, long done, long total, String status, String error,
                                    String message) {
        Job persisted = get(old.jobId());
        if (persisted == null && removed.contains(old.jobId())) return old;
        long lastSeq = persisted != null ? persisted.lastSeq() : old.lastSeq();
        long createdAt = persisted != null ? persisted.createdAt() : old.createdAt();
        Job next = new Job(old.jobId(), old.version(), phase, done, total, status, error, message,
                createdAt, System.currentTimeMillis(), lastSeq);
        save(next);
        return next;
    }

    private synchronized void save(Job job) {
        if (removed.contains(job.jobId())) return;
        List<Map<String, Object>> rows = store.jobs();
        List<Map<String, Object>> next = new ArrayList<>();
        boolean replaced = false;
        for (Map<String, Object> row : rows) {
            if (String.valueOf(row.get("jobId")).equals(job.jobId())) {
                next.add(map(job));
                replaced = true;
            } else {
                next.add(row);
            }
        }
        if (!replaced) next.add(map(job));
        store.writeJobs(next);
    }

    private synchronized void emit(Job job, String type, String message, String level) {
        if (removed.contains(job.jobId())) return;
        Job persisted = get(job.jobId());
        if (persisted == null) return;
        long seq = persisted.lastSeq() + 1;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("seq", seq);
        payload.put("type", type);
        payload.put("level", level);
        payload.put("message", message);
        payload.put("jobId", persisted.jobId());
        payload.put("version", persisted.version());
        payload.put("phase", persisted.phase());
        payload.put("done", persisted.done());
        payload.put("total", persisted.total());
        payload.put("status", persisted.status());
        payload.put("updatedAt", persisted.updatedAt());
        store.appendEvent(persisted.jobId(), payload);
        Job next = new Job(persisted.jobId(), persisted.version(), persisted.phase(), persisted.done(),
                persisted.total(), persisted.status(), persisted.error(), persisted.message(),
                persisted.createdAt(), persisted.updatedAt(), seq);
        save(next);
    }

    private Map<String, Object> map(Job job) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jobId", job.jobId());
        row.put("version", job.version());
        row.put("phase", job.phase());
        row.put("done", job.done());
        row.put("total", job.total());
        row.put("status", job.status());
        row.put("error", job.error());
        row.put("message", job.message());
        row.put("createdAt", job.createdAt());
        row.put("updatedAt", job.updatedAt());
        row.put("lastSeq", job.lastSeq());
        return row;
    }

    private Job read(Map<String, Object> row) {
        return new Job(String.valueOf(row.get("jobId")), String.valueOf(row.get("version")),
                String.valueOf(row.getOrDefault("phase", "QUEUE")), number(row.get("done")), number(row.get("total")),
                String.valueOf(row.getOrDefault("status", "PENDING")),
                row.get("error") == null ? null : String.valueOf(row.get("error")),
                row.get("message") == null ? null : String.valueOf(row.get("message")),
                number(row.get("createdAt")), number(row.get("updatedAt")), number(row.get("lastSeq")));
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /** 任务执行侧的控制台：检查暂停/取消并在安全点让出。 */
    final class Control {
        private final Job job;
        private volatile Job current;

        private Control(Job job) {
            this.job = job;
            this.current = job;
        }

        void setStatus(String status) {
            current = update(current, current.phase(), current.done(), current.total(), status, null, current.message());
            emit(current, "state", current.message(), "INFO");
        }

        void checkpoint() {
            AtomicBoolean paused = pauses.get(job.jobId());
            boolean announced = false;
            while (paused != null && paused.get()) {
                if (!announced) {
                    current = update(current, current.phase(), current.done(), current.total(),
                            "PAUSED", null, "任务已暂停");
                    emit(current, "state", "任务已暂停", "INFO");
                    announced = true;
                }
                if (cancelled()) throw new Cancelled();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new Cancelled();
                }
            }
            if (announced) {
                current = update(current, current.phase(), current.done(), current.total(),
                        "RUNNING", null, "任务已继续");
                emit(current, "state", "任务已继续", "INFO");
            }
            if (cancelled()) throw new Cancelled();
        }

        void progress(String phase, long done, long total, String message) {
            checkpoint();
            current = update(current, phase, done, total, "RUNNING", null, message);
            emit(current, "progress", message, "INFO");
        }

        void log(String level, String message) {
            current = update(current, current.phase(), current.done(), current.total(),
                    current.status(), null, message);
            emit(current, "log", message, level);
        }

        private boolean cancelled() {
            AtomicBoolean flag = cancels.get(job.jobId());
            return flag != null && flag.get();
        }

        private Job finish(String status, String error, String message) {
            long done = "DONE".equals(status) ? current.total() : current.done();
            current = update(current, current.phase(), done, current.total(), status, error, message);
            return current;
        }
    }
}
