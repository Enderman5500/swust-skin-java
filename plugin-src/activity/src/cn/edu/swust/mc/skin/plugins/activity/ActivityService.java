package cn.edu.swust.mc.skin.plugins.activity;

import cn.edu.swust.mc.skin.AuthUser;
import cn.edu.swust.mc.skin.Web;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 活动、报名、核验、身份与证明导出的业务逻辑。 */
final class ActivityService {
    static final String STATUS_DRAFT = "DRAFT";
    static final String STATUS_OPEN = "OPEN";
    static final String STATUS_CLOSED = "CLOSED";
    static final String VERIFY_PENDING = "PENDING";
    static final String VERIFY_PASSED = "PASSED";
    static final String VERIFY_REJECTED = "REJECTED";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy年M月d日");

    record Eligibility(String status, int grade, String label, boolean proofAllowed,
                       int enrollmentYear, int graduationYear) {
    }

    private final ActivityStore store;

    ActivityService(ActivityStore store) {
        this.store = store;
    }

    // ---------------------------------------------------------------- 活动

    Map<String, Object> publicActivities(AuthUser user, String keyword, String status) {
        String query = lower(keyword);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> activity : sorted(store.activities())) {
            String value = str(activity.get("status"));
            if (STATUS_DRAFT.equals(value)) continue;
            if (!status.isBlank() && !status.equals(value)) continue;
            if (!query.isEmpty() && !str(activity.get("title")).toLowerCase(Locale.ROOT).contains(query)) continue;
            rows.add(publicRow(activity, user));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows);
        result.put("total", rows.size());
        return result;
    }

    Map<String, Object> publicActivity(String id, AuthUser user) {
        Map<String, Object> activity = requireActivity(id);
        if (STATUS_DRAFT.equals(str(activity.get("status"))) && (user == null || !user.admin)) {
            throw Web.notFound("活动不存在或尚未发布");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activity", publicRow(activity, user));
        result.put("stats", stats(id));
        result.put("mine", user == null ? null : participation(id, user.id));
        return result;
    }

    synchronized Map<String, Object> saveActivity(Map<String, Object> body, AuthUser admin) {
        String id = str(body.get("id")).trim();
        List<Map<String, Object>> activities = store.activities();
        Map<String, Object> existing = null;
        if (!id.isEmpty()) {
            existing = find(activities, id);
            if (existing == null) throw Web.notFound("活动不存在");
        }
        String title = required(body.get("title"), "活动名称", 80);
        String summary = optional(body.get("summary"), 200);
        String description = optional(body.get("description"), 5000);
        String location = optional(body.get("location"), 100);
        String startTime = time(body.get("startTime"));
        String endTime = time(body.get("endTime"));
        String signupStart = time(body.get("signupStart"));
        String signupEnd = time(body.get("signupEnd"));
        if (!startTime.isEmpty() && !endTime.isEmpty() && endTime.compareTo(startTime) < 0) {
            throw Web.badRequest("活动结束时间不能早于开始时间");
        }
        if (!signupStart.isEmpty() && !signupEnd.isEmpty() && signupEnd.compareTo(signupStart) < 0) {
            throw Web.badRequest("报名截止时间不能早于报名开始时间");
        }
        int capacity = Math.max(0, Math.min(9999, (int) number(body.get("capacity"), 0)));
        Map<String, Object> activity;
        if (existing == null) {
            activity = new LinkedHashMap<>();
            activity.put("id", "act_" + Long.toHexString(System.nanoTime()));
            activity.put("status", STATUS_DRAFT);
            activity.put("createdAt", Instant.now().toString());
            activity.put("createdBy", admin.username);
            activities.add(activity);
        } else {
            activity = existing;
        }
        activity.put("title", title);
        activity.put("summary", summary);
        activity.put("description", description);
        activity.put("location", location);
        activity.put("startTime", startTime);
        activity.put("endTime", endTime);
        activity.put("signupStart", signupStart);
        activity.put("signupEnd", signupEnd);
        activity.put("capacity", capacity);
        activity.put("proofEnabled", bool(body.get("proofEnabled"), true));
        activity.put("autoSignup", bool(body.get("autoSignup"), false));
        activity.put("updatedAt", Instant.now().toString());
        store.writeActivities(activities);
        return publicRow(activity, admin);
    }

    synchronized Map<String, Object> setStatus(String id, String status) {
        if (!List.of(STATUS_DRAFT, STATUS_OPEN, STATUS_CLOSED).contains(status)) {
            throw Web.badRequest("活动状态不合法");
        }
        List<Map<String, Object>> activities = store.activities();
        Map<String, Object> activity = find(activities, id);
        if (activity == null) throw Web.notFound("活动不存在");
        activity.put("status", status);
        activity.put("updatedAt", Instant.now().toString());
        store.writeActivities(activities);
        return publicRow(activity, null);
    }

    synchronized void deleteActivity(String id) {
        List<Map<String, Object>> activities = store.activities();
        Map<String, Object> activity = find(activities, id);
        if (activity == null) throw Web.notFound("活动不存在");
        activities.remove(activity);
        store.writeActivities(activities);
        List<Map<String, Object>> participations = store.participations();
        participations.removeIf(row -> id.equals(str(row.get("activityId"))));
        store.writeParticipations(participations);
    }

    Map<String, Object> adminActivities(String keyword, String status, int page, int size) {
        String query = lower(keyword);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> activity : sorted(store.activities())) {
            if (!status.isBlank() && !status.equals(str(activity.get("status")))) continue;
            if (!query.isEmpty() && !str(activity.get("title")).toLowerCase(Locale.ROOT).contains(query)) continue;
            Map<String, Object> row = publicRow(activity, null);
            row.put("stats", stats(str(activity.get("id"))));
            rows.add(row);
        }
        return page(rows, page, size);
    }

    // ---------------------------------------------------------------- 报名与核验

    synchronized Map<String, Object> signup(AuthUser user, String activityId) {
        Map<String, Object> activity = requireActivity(activityId);
        ensurePublished(activity);
        if (!signupOpen(activity, joinedCount(activityId))) {
            throw Web.badRequest("活动当前不可报名（未开放、已截止或人数已满）");
        }
        List<Map<String, Object>> rows = store.participations();
        Map<String, Object> existing = findParticipation(rows, activityId, user.id);
        String now = Instant.now().toString();
        if (existing != null) {
            if ("JOINED".equals(str(existing.get("status")))) throw Web.conflict("你已经报名该活动");
            existing.put("status", "JOINED");
            existing.put("verify", VERIFY_PENDING);
            existing.put("verifyNote", "");
            existing.put("joinedAt", now);
            existing.put("updatedAt", now);
        } else {
            existing = new LinkedHashMap<>();
            existing.put("activityId", activityId);
            existing.put("userId", user.id);
            existing.put("username", user.username);
            existing.put("nickname", user.nickname);
            existing.put("source", "SELF");
            existing.put("status", "JOINED");
            existing.put("verify", VERIFY_PENDING);
            existing.put("verifyNote", "");
            existing.put("joinedAt", now);
            existing.put("updatedAt", now);
            rows.add(existing);
        }
        store.writeParticipations(rows);
        return participationView(activity, existing);
    }

    synchronized Map<String, Object> cancel(AuthUser user, String activityId) {
        requireActivity(activityId);
        List<Map<String, Object>> rows = store.participations();
        Map<String, Object> existing = findParticipation(rows, activityId, user.id);
        if (existing == null || !"JOINED".equals(str(existing.get("status")))) {
            throw Web.badRequest("你没有报名该活动");
        }
        existing.put("status", "CANCELLED");
        existing.put("verify", VERIFY_PENDING);
        existing.put("updatedAt", Instant.now().toString());
        store.writeParticipations(rows);
        return Map.of("cancelled", true);
    }

    Map<String, Object> adminActivity(String id, String keyword, String verify, int page, int size) {
        Map<String, Object> activity = requireActivity(id);
        Map<String, Map<String, Object>> identities = store.identities();
        String query = lower(keyword);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : store.participations()) {
            if (!id.equals(str(row.get("activityId")))) continue;
            if (!"JOINED".equals(str(row.get("status")))) continue;
            if (!verify.isBlank() && !verify.equals(str(row.get("verify")))) continue;
            Map<String, Object> identity = identities.get(str(row.get("userId")));
            String name = identity == null ? "" : str(identity.get("realName"));
            String studentNo = identity == null ? "" : str(identity.get("studentNo"));
            if (!query.isEmpty()) {
                String haystack = (str(row.get("username")) + " " + str(row.get("nickname")) + " " + name + " " + studentNo)
                        .toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) continue;
            }
            Map<String, Object> view = participationView(activity, row);
            view.put("realName", name);
            view.put("studentNo", studentNo);
            view.put("college", identity == null ? "" : str(identity.get("college")));
            view.put("className", identity == null ? "" : str(identity.get("className")));
            rows.add(view);
        }
        rows.sort(Comparator.comparing((Map<String, Object> row) -> str(row.get("joinedAt"))).reversed());
        Map<String, Object> result = page(rows, page, size);
        result.put("activity", publicRow(activity, null));
        result.put("stats", stats(id));
        return result;
    }

    synchronized List<Map<String, Object>> verify(String activityId, List<String> userIds, String verify, String note,
                                                  AuthUser admin) {
        if (!List.of(VERIFY_PENDING, VERIFY_PASSED, VERIFY_REJECTED).contains(verify)) {
            throw Web.badRequest("核验状态不合法");
        }
        requireActivity(activityId);
        List<Map<String, Object>> rows = store.participations();
        List<Map<String, Object>> updated = new ArrayList<>();
        String now = Instant.now().toString();
        for (String userId : userIds) {
            Map<String, Object> row = findParticipation(rows, activityId, userId);
            if (row == null || !"JOINED".equals(str(row.get("status")))) continue;
            row.put("verify", verify);
            row.put("verifyNote", optional(note, 200));
            row.put("verifiedAt", now);
            row.put("verifiedBy", admin.username);
            row.put("updatedAt", now);
            updated.add(row);
        }
        store.writeParticipations(rows);
        return updated;
    }

    // ---------------------------------------------------------------- 身份

    Map<String, Object> me(AuthUser user) {
        Map<String, Object> identity = store.identities().get(user.id);
        Map<String, Object> view = identityView(user, identity);
        List<Map<String, Object>> mine = new ArrayList<>();
        for (Map<String, Object> participation : store.participations()) {
            if (!user.id.equals(str(participation.get("userId")))) continue;
            if (!"JOINED".equals(str(participation.get("status")))) continue;
            Map<String, Object> activity = find(store.activities(), str(participation.get("activityId")));
            if (activity == null) continue;
            Map<String, Object> row = participationView(activity, participation);
            row.put("activityStatus", str(activity.get("status")));
            mine.add(row);
        }
        mine.sort(Comparator.comparing((Map<String, Object> row) -> str(row.get("joinedAt"))).reversed());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("identity", view);
        result.put("activities", mine);
        result.put("verifiedCount", mine.stream()
                .filter(row -> VERIFY_PASSED.equals(str(row.get("verify")))).count());
        return result;
    }

    synchronized Map<String, Object> saveIdentity(AuthUser user, Map<String, Object> body) {
        boolean optIn = bool(body.get("proofOptIn"), false);
        String realName = optional(body.get("realName"), 30);
        String studentNo = optional(body.get("studentNo"), 20);
        String college = optional(body.get("college"), 60);
        String className = optional(body.get("className"), 60);
        String major = optional(body.get("major"), 60);
        int graduationYear = number(body.get("graduationYear"), 0);
        boolean continueParticipate = bool(body.get("continueParticipate"), false);
        if (optIn) {
            if (realName.isBlank()) throw Web.badRequest("参与活动证明需要填写姓名");
            if (!studentNo.matches("^51\\d{8}$")) {
                throw Web.badRequest("学号格式应为 51 + 4 位入学年份 + 4 位数字");
            }
            if (className.isBlank()) throw Web.badRequest("参与活动证明需要填写班级");
            int enrollmentYear = Integer.parseInt(studentNo.substring(2, 6));
            if (graduationYear == 0) graduationYear = enrollmentYear + 4;
            if (graduationYear < enrollmentYear || graduationYear > enrollmentYear + 10) {
                throw Web.badRequest("预计毕业年份应在入学年份至入学年份 + 10 年之间");
            }
        }
        Map<String, Map<String, Object>> identities = store.identities();
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("userId", user.id);
        identity.put("username", user.username);
        identity.put("proofOptIn", optIn);
        identity.put("realName", realName);
        identity.put("studentNo", studentNo);
        identity.put("college", college);
        identity.put("className", className);
        identity.put("major", major);
        if (graduationYear > 0) identity.put("graduationYear", graduationYear);
        identity.put("continueParticipate", continueParticipate);
        identity.put("updatedAt", Instant.now().toString());
        identities.put(user.id, identity);
        store.writeIdentities(identities);
        return identityView(user, identity);
    }

    Map<String, Object> identities(String keyword, String eligibilityFilter, int page, int size) {
        String query = lower(keyword);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> identity : store.identities().values()) {
            Map<String, Object> view = new LinkedHashMap<>(identity);
            Eligibility eligibility = eligibility(str(identity.get("studentNo")), identity.get("graduationYear"));
            view.put("eligibility", eligibility.status());
            view.put("grade", eligibility.grade());
            view.put("gradeLabel", eligibility.label());
            view.put("proofAllowed", eligibility.proofAllowed()
                    || bool(identity.get("continueParticipate"), false));
            view.put("enrollmentYear", eligibility.enrollmentYear());
            view.put("graduationYear", eligibility.graduationYear());
            if (!query.isEmpty()) {
                String haystack = (str(identity.get("username")) + " " + str(identity.get("realName")) + " "
                        + str(identity.get("studentNo"))).toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) continue;
            }
            if (!eligibilityFilter.isBlank() && !eligibilityFilter.equals(eligibility.status())) continue;
            rows.add(view);
        }
        rows.sort(Comparator.comparing((Map<String, Object> row) -> str(row.get("updatedAt"))).reversed());
        return page(rows, page, size);
    }

    // ---------------------------------------------------------------- 模板与设置

    Map<String, Object> settings() {
        Map<String, Object> settings = store.settings();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("templateUploaded", store.hasTemplate());
        result.put("templateName", repairFileName(str(settings.get("templateName"))));
        result.put("templateSize", number(settings.get("templateSize"), 0));
        result.put("templateUploadedAt", str(settings.get("templateUploadedAt")));
        result.put("defaultIssuer", str(settings.get("defaultIssuer")));
        result.put("defaultCollege", str(settings.get("defaultCollege")));
        result.put("defaultProofPrefix", str(settings.get("defaultProofPrefix")).isBlank()
                ? "SWUSTMC" : str(settings.get("defaultProofPrefix")));
        return result;
    }

    synchronized Map<String, Object> saveSettings(Map<String, Object> body) {
        Map<String, Object> settings = store.settings();
        settings.put("defaultIssuer", optional(body.get("defaultIssuer"), 60));
        settings.put("defaultCollege", optional(body.get("defaultCollege"), 60));
        settings.put("defaultProofPrefix", optional(body.get("defaultProofPrefix"), 30));
        settings.put("updatedAt", Instant.now().toString());
        store.writeSettings(settings);
        return settings();
    }

    synchronized Map<String, Object> saveTemplate(String filename, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) throw Web.badRequest("请选择 DOCX 模板文件");
        if (bytes.length > 12 * 1024 * 1024) throw Web.badRequest("模板文件不能超过 12MB");
        ProofService.validateTemplate(bytes);
        store.writeTemplate(bytes);
        Map<String, Object> settings = store.settings();
        String name = filename == null || filename.isBlank() ? "template.docx" : repairFileName(filename.trim());
        if (!name.toLowerCase(Locale.ROOT).endsWith(".docx")) name = name + ".docx";
        settings.put("templateName", name);
        settings.put("templateSize", bytes.length);
        settings.put("templateUploadedAt", Instant.now().toString());
        store.writeSettings(settings);
        return settings();
    }

    byte[] templateBytes() throws IOException {
        byte[] bytes = store.template();
        if (bytes == null) throw Web.notFound("尚未上传活动证明模板");
        return bytes;
    }

    String templateName() {
        String name = repairFileName(str(store.settings().get("templateName")));
        return name.isBlank() ? "template.docx" : name;
    }

    // ---------------------------------------------------------------- 导出

    Map<String, Object> exportPreview(List<String> activityIds, String mode) {
        List<Map<String, Object>> activities = selectedActivities(activityIds);
        String effectiveMode = "all".equalsIgnoreCase(mode) ? "all" : "any";
        Map<String, Set<String>> passed = passedActivities(activities);
        Map<String, Map<String, Object>> identities = store.identities();
        List<Map<String, Object>> eligible = new ArrayList<>();
        List<Map<String, Object>> excluded = new ArrayList<>();
        Set<String> seenStudents = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : passed.entrySet()) {
            String userId = entry.getKey();
            if ("all".equals(effectiveMode) && entry.getValue().size() < activities.size()) continue;
            Map<String, Object> identity = identities.get(userId);
            ParticipationUser user = participationUser(userId);
            List<Map<String, Object>> matched = new ArrayList<>();
            for (Map<String, Object> activity : activities) {
                if (entry.getValue().contains(str(activity.get("id")))) matched.add(activity);
            }
            Map<String, Object> row = participantRow("ACTIVITY", userId, user.username, user.nickname,
                    identity == null ? "" : identity.get("realName"),
                    identity == null ? "" : identity.get("studentNo"),
                    identity == null ? "" : identity.get("college"),
                    identity == null ? "" : identity.get("className"),
                    identity == null ? "" : identity.get("major"),
                    identity == null ? "" : identity.get("graduationYear"),
                    matched);
            boolean optIn = identity != null && bool(identity.get("proofOptIn"), false);
            boolean continueParticipate = identity != null && bool(identity.get("continueParticipate"), false);
            classify(row, optIn, continueParticipate);
            if (Boolean.TRUE.equals(row.get("included"))) {
                if (!str(row.get("studentNo")).isBlank()) seenStudents.add(str(row.get("studentNo")));
                eligible.add(row);
            } else {
                excluded.add(row);
            }
        }
        for (Map<String, Object> fixed : fixedParticipants()) {
            String studentNo = str(fixed.get("studentNo"));
            if (!studentNo.isBlank() && seenStudents.contains(studentNo)) continue;
            Map<String, Object> row = participantRow("FIXED", "fixed:" + str(fixed.get("id")),
                    str(fixed.get("name")), str(fixed.get("name")), fixed.get("name"), fixed.get("studentNo"),
                    fixed.get("college"), fixed.get("className"), fixed.get("major"), fixed.get("graduationYear"),
                    activities);
            classify(row, true, false);
            if (Boolean.TRUE.equals(row.get("included"))) {
                eligible.add(row);
                if (!studentNo.isBlank()) seenStudents.add(studentNo);
            } else {
                excluded.add(row);
            }
        }
        eligible.sort(Comparator.comparing(row -> str(row.get("realName"))));
        excluded.sort(Comparator.comparing(row -> str(row.get("realName"))));
        Map<String, Object> settings = store.settings();
        String prefix = str(settings.get("defaultProofPrefix"));
        if (prefix.isBlank()) prefix = "SWUSTMC";
        String stamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        List<String> titles = activities.stream().map(activity -> str(activity.get("title"))).toList();
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("proofNo", prefix + "-" + stamp);
        defaults.put("issueDate", LocalDate.now().format(DATE));
        defaults.put("issuer", str(settings.get("defaultIssuer")));
        defaults.put("activityText", String.join("、", titles));
        defaults.put("proofNoPrefix", prefix);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", effectiveMode);
        result.put("activityIds", activities.stream().map(activity -> str(activity.get("id"))).toList());
        result.put("activityTitles", titles);
        result.put("total", eligible.size() + excluded.size());
        result.put("eligibleCount", eligible.size());
        result.put("excludedCount", excluded.size());
        result.put("eligible", eligible);
        result.put("excluded", excluded);
        result.put("defaults", defaults);
        result.put("fixedCount", fixedParticipants().size());
        return result;
    }

    private Map<String, Object> participantRow(String source, String userId, String username, String nickname,
                                               Object realName, Object studentNo, Object college, Object className,
                                               Object major, Object graduationYear,
                                               List<Map<String, Object>> activities) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source", source);
        row.put("userId", userId);
        row.put("username", username);
        row.put("nickname", nickname);
        row.put("realName", str(realName));
        row.put("studentNo", str(studentNo));
        row.put("college", str(college));
        row.put("className", str(className));
        row.put("major", str(major));
        row.put("graduationYear", graduationYear);
        row.put("activityIds", activities.stream().map(activity -> str(activity.get("id"))).toList());
        row.put("activities", activities.stream().map(activity -> str(activity.get("title"))).toList());
        return row;
    }

    private void classify(Map<String, Object> row, boolean optIn, boolean continueParticipate) {
        Eligibility eligibility = eligibility(row.get("studentNo"), row.get("graduationYear"));
        row.put("eligibility", eligibility.status());
        row.put("grade", eligibility.grade());
        row.put("gradeLabel", eligibility.label());
        row.put("enrollmentYear", eligibility.enrollmentYear());
        if (eligibility.graduationYear() > 0) row.put("graduationYear", eligibility.graduationYear());
        row.put("defaultAllowed", eligibility.proofAllowed());
        row.put("continueParticipate", continueParticipate);
        row.put("proofAllowed", optIn && (eligibility.proofAllowed() || continueParticipate));
        String reason = exclusionReason(optIn, continueParticipate, row, eligibility);
        row.put("included", reason == null);
        if (reason != null) row.put("reason", reason);
    }

    synchronized Map<String, Object> exportProofs(Map<String, Object> body, AuthUser admin) throws IOException {
        List<String> activityIds = stringList(body.get("activityIds"));
        String mode = str(body.get("mode"));
        Map<String, Object> preview = exportPreview(activityIds, mode);
        byte[] template = store.template();
        if (template == null) throw Web.badRequest("请先上传活动证明模板");
        Map<String, Object> settings = store.settings();
        @SuppressWarnings("unchecked")
        Map<String, Object> defaults = (Map<String, Object>) preview.getOrDefault("defaults", Map.of());
        String issuer = optional(body.get("issuer"), 60);
        if (issuer.isBlank()) issuer = str(defaults.get("issuer"));
        String issueDate = optional(body.get("issueDate"), 30);
        if (issueDate.isBlank()) issueDate = str(defaults.get("issueDate"));
        String proofNo = optional(body.get("proofNo"), 40);
        if (proofNo.isBlank()) proofNo = str(defaults.get("proofNo"));
        String activityText = optional(body.get("activityText"), 300);
        if (activityText.isBlank()) activityText = str(defaults.get("activityText"));

        List<Map<String, Object>> rows = new ArrayList<>();
        int excluded = 0;
        Object rawRows = body.get("rows");
        if (rawRows instanceof List<?> list && !list.isEmpty()) {
            Set<String> seen = new LinkedHashSet<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("realName", optional(raw.get("realName"), 40));
                row.put("studentNo", optional(raw.get("studentNo"), 20));
                row.put("className", optional(raw.get("className"), 80));
                row.put("college", optional(raw.get("college"), 80));
                row.put("major", optional(raw.get("major"), 80));
                row.put("graduationYear", raw.get("graduationYear"));
                row.put("activities", stringList(body.get("activities")));
                classify(row, true, bool(raw.get("continueParticipate"), false));
                String studentNo = str(row.get("studentNo"));
                if (!Boolean.TRUE.equals(row.get("included")) || studentNo.isBlank() || seen.contains(studentNo)) {
                    excluded++;
                    continue;
                }
                seen.add(studentNo);
                rows.add(row);
            }
        } else {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> eligible = (List<Map<String, Object>>) preview.get("eligible");
            rows.addAll(eligible);
            excluded = number(preview.get("excludedCount"), 0);
        }
        if (rows.isEmpty()) throw Web.badRequest("没有符合条件的参与者，请检查学号、身份信息与年级");

        List<Map<String, String>> tableRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("name", str(row.get("realName")));
            item.put("className", str(row.get("className")));
            item.put("studentNo", str(row.get("studentNo")));
            tableRows.add(item);
        }
        Map<String, String> values = new LinkedHashMap<>();
        put(values, "proofNo", proofNo);
        put(values, "issueDate", issueDate);
        put(values, "issuer", issuer);
        put(values, "activities", activityText);
        put(values, "activityCount", String.valueOf(activityIds.size()));
        put(values, "generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        byte[] docx = ProofService.renderGroup(template, values, tableRows);

        String exportId = "exp_" + Long.toHexString(System.nanoTime());
        String stamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String filename = safeFileName("活动证明_" + (activityText.isBlank() ? proofNo : activityText) + "_" + stamp
                + ".docx");
        if (filename.length() > 120) filename = filename.substring(0, 110) + ".docx";
        store.saveExport(exportId, docx);

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", exportId);
        record.put("activityIds", preview.get("activityIds"));
        record.put("activityTitles", preview.get("activityTitles"));
        record.put("mode", preview.get("mode"));
        record.put("count", rows.size());
        record.put("excludedCount", excluded);
        record.put("filename", filename);
        record.put("issuer", issuer);
        record.put("issueDate", issueDate);
        record.put("proofNo", proofNo);
        record.put("createdAt", Instant.now().toString());
        record.put("createdBy", admin.username);
        record.put("snapshots", rows);
        List<Map<String, Object>> records = store.exports();
        records.add(0, record);
        if (records.size() > 100) records = new ArrayList<>(records.subList(0, 100));
        store.writeExports(records);
        return exportSummary(record);
    }

    // ---------------------------------------------------------------- 固定参与人员

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> fixedParticipants() {
        Object value = store.settings().get("fixedParticipants");
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) rows.add((Map<String, Object>) map);
        }
        return rows;
    }

    Map<String, Object> fixedCandidates(String keyword) {
        String query = lower(keyword);
        Set<String> fixedNos = new LinkedHashSet<>();
        for (Map<String, Object> row : fixedParticipants()) fixedNos.add(str(row.get("studentNo")));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> identity : store.identities().values()) {
            String studentNo = str(identity.get("studentNo"));
            String realName = str(identity.get("realName"));
            if (studentNo.isBlank() || realName.isBlank()) continue;
            if (fixedNos.contains(studentNo)) continue;
            String haystack = (str(identity.get("username")) + " " + realName + " " + studentNo
                    + " " + str(identity.get("className"))).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", identity.get("userId"));
            row.put("username", identity.get("username"));
            row.put("realName", realName);
            row.put("studentNo", studentNo);
            row.put("className", identity.get("className"));
            row.put("college", identity.get("college"));
            row.put("major", identity.get("major"));
            row.put("graduationYear", identity.get("graduationYear"));
            rows.add(row);
        }
        rows.sort(Comparator.comparing(row -> str(row.get("realName"))));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows);
        result.put("total", rows.size());
        return result;
    }

    synchronized Map<String, Object> saveFixedParticipant(Map<String, Object> body) {
        String name = required(body.get("name"), "姓名", 40);
        String className = required(body.get("className"), "专业班级", 80);
        String studentNo = required(body.get("studentNo"), "学号", 20);
        if (!studentNo.matches("^51\\d{8}$")) throw Web.badRequest("固定人员学号格式应为 51 + 4 位入学年份 + 4 位数字");
        int graduationYear = number(body.get("graduationYear"), Integer.parseInt(studentNo.substring(2, 6)) + 4);
        Map<String, Object> row = new LinkedHashMap<>();
        String id = optional(body.get("id"), 40);
        row.put("id", id.isBlank() ? "fixed_" + Long.toHexString(System.nanoTime()) : id);
        row.put("name", name);
        row.put("className", className);
        row.put("studentNo", studentNo);
        row.put("college", optional(body.get("college"), 80));
        row.put("major", optional(body.get("major"), 80));
        row.put("graduationYear", graduationYear);
        Map<String, Object> settings = store.settings();
        List<Map<String, Object>> rows = new ArrayList<>(fixedParticipants());
        rows.removeIf(item -> str(row.get("id")).equals(str(item.get("id"))));
        rows.add(row);
        settings.put("fixedParticipants", rows);
        store.writeSettings(settings);
        return row;
    }

    synchronized void deleteFixedParticipant(String id) {
        Map<String, Object> settings = store.settings();
        List<Map<String, Object>> rows = new ArrayList<>(fixedParticipants());
        rows.removeIf(item -> id.equals(str(item.get("id"))));
        settings.put("fixedParticipants", rows);
        store.writeSettings(settings);
    }

    // ---------------------------------------------------------------- 在线模板编辑

    List<Map<String, Object>> templateParagraphs() throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProofService.Paragraph paragraph : ProofService.paragraphs(templateBytes())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", paragraph.index());
            row.put("text", paragraph.text());
            rows.add(row);
        }
        return rows;
    }

    synchronized List<Map<String, Object>> saveTemplateParagraphs(List<Map<String, Object>> paragraphs) throws IOException {
        Map<Integer, String> texts = new LinkedHashMap<>();
        for (Map<String, Object> row : paragraphs) {
            texts.put(number(row.get("index"), -1), str(row.get("text")));
        }
        byte[] updated = ProofService.updateParagraphs(templateBytes(), texts);
        store.writeTemplate(updated);
        Map<String, Object> settings = store.settings();
        settings.put("templateSize", updated.length);
        settings.put("templateUpdatedAt", Instant.now().toString());
        store.writeSettings(settings);
        return templateParagraphs();
    }

    Map<String, Object> exports() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> record : store.exports()) rows.add(exportSummary(record));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows);
        result.put("total", rows.size());
        return result;
    }

    synchronized void deleteExport(String id) {
        if (id == null || !id.matches("exp_[0-9a-f]+")) throw Web.notFound("导出记录不存在");
        List<Map<String, Object>> records = store.exports();
        boolean removed = records.removeIf(record -> id.equals(str(record.get("id"))));
        if (!removed) throw Web.notFound("导出记录不存在");
        store.writeExports(records);
        store.deleteExport(id);
    }

    byte[] exportFile(String id) throws IOException {
        if (id == null || !id.matches("exp_[0-9a-f]+")) throw Web.notFound("导出文件不存在");
        byte[] zip = store.exportZip(id);
        if (zip == null) throw Web.notFound("导出文件不存在");
        return zip;
    }

    String exportFilename(String id) {
        for (Map<String, Object> record : store.exports()) {
            if (id.equals(str(record.get("id")))) return str(record.get("filename"));
        }
        return "活动证明.zip";
    }

    /** 用户为自己的已核验活动生成证明（单份 DOCX，适合直接下载或打印）。 */
    byte[] userProof(AuthUser user, List<String> activityIds) throws IOException {
        Map<String, Object> identity = store.identities().get(user.id);
        if (identity == null || !bool(identity.get("proofOptIn"), false)) {
            throw Web.badRequest("你尚未开启活动证明，请先在「我的资料」中填写身份信息");
        }
        Eligibility eligibility = eligibility(str(identity.get("studentNo")), identity.get("graduationYear"));
        boolean continueParticipate = bool(identity.get("continueParticipate"), false);
        if (!eligibility.proofAllowed() && !continueParticipate) {
            throw Web.badRequest(eligibility.label() + "已过默认参与时间段，请在身份信息中选择继续参与");
        }
        List<Map<String, Object>> activities = new ArrayList<>();
        List<String> passed = new ArrayList<>();
        Set<String> requested = new LinkedHashSet<>(activityIds);
        for (Map<String, Object> activity : sorted(store.activities())) {
            String id = str(activity.get("id"));
            if (!requested.isEmpty() && !requested.contains(id)) continue;
            Map<String, Object> participation = findParticipation(store.participations(), id, user.id);
            if (participation == null || !"JOINED".equals(str(participation.get("status")))) continue;
            if (!VERIFY_PASSED.equals(str(participation.get("verify")))) continue;
            activities.add(activity);
            passed.add(id);
        }
        if (activities.isEmpty()) throw Web.badRequest("没有已核验通过的活动");
        byte[] template = store.template();
        if (template == null) throw Web.badRequest("管理员尚未上传活动证明模板");
        Map<String, Object> settings = store.settings();
        String prefix = str(settings.get("defaultProofPrefix"));
        if (prefix.isBlank()) prefix = "SWUSTMC";
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("realName", str(identity.get("realName")));
        row.put("studentNo", str(identity.get("studentNo")));
        row.put("college", str(identity.get("college")));
        row.put("className", str(identity.get("className")));
        row.put("major", str(identity.get("major")));
        row.put("username", user.username);
        row.put("graduationYear", identity.get("graduationYear"));
        row.put("activities", activities.stream().map(activity -> str(activity.get("title"))).toList());
        row.put("activityIds", passed);
        String proofNo = prefix + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-SELF";
        String issuer = str(settings.get("defaultIssuer"));
        Map<String, String> values = proofValues(row, proofNo, LocalDate.now().format(DATE), issuer);
        List<Map<String, String>> tableRows = new ArrayList<>();
        Map<String, String> tableRow = new LinkedHashMap<>();
        tableRow.put("name", str(identity.get("realName")));
        tableRow.put("className", str(identity.get("className")));
        tableRow.put("studentNo", str(identity.get("studentNo")));
        tableRows.add(tableRow);
        return ProofService.renderGroup(template, values, tableRows);
    }

    String userProofFilename(AuthUser user) {
        Map<String, Object> identity = store.identities().get(user.id);
        String name = identity == null ? "" : str(identity.get("realName"));
        return safeFileName((name.isBlank() ? user.username : name) + "_活动证明.docx");
    }

    // ---------------------------------------------------------------- 已盖章 PDF

    synchronized Map<String, Object> prepareStamped(Map<String, Object> body, AuthUser admin) {
        String source = "EXPORT".equalsIgnoreCase(str(body.get("source"))) ? "EXPORT" : "MANUAL";
        List<Map<String, Object>> participants = new ArrayList<>();
        List<String> activityTitles = new ArrayList<>();
        String title = optional(body.get("title"), 100);
        String exportId = optional(body.get("exportId"), 40);
        if ("EXPORT".equals(source)) {
            Map<String, Object> record = findExport(exportId);
            if (record == null) throw Web.badRequest("导出记录不存在");
            activityTitles = stringList(record.get("activityTitles"));
            if (title.isBlank()) title = str(record.get("filename")).replaceAll("(?i)\\.docx$", "");
            Object snapshots = record.get("snapshots");
            if (snapshots instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> raw)) continue;
                    Map<String, Object> participant = new LinkedHashMap<>();
                    participant.put("userId", str(raw.get("userId")));
                    participant.put("username", str(raw.get("username")));
                    participant.put("realName", str(raw.get("realName")));
                    participant.put("studentNo", str(raw.get("studentNo")));
                    participant.put("className", str(raw.get("className")));
                    participant.put("college", str(raw.get("college")));
                    if (!str(participant.get("studentNo")).isBlank()) participants.add(participant);
                }
            }
        } else {
            List<String> userIds = stringList(body.get("userIds"));
            if (userIds.isEmpty()) throw Web.badRequest("请选择参与名单");
            Map<String, Map<String, Object>> identities = store.identities();
            for (String userId : userIds) {
                Map<String, Object> identity = identities.get(userId);
                if (identity == null) continue;
                Map<String, Object> participant = new LinkedHashMap<>();
                participant.put("userId", userId);
                participant.put("username", str(identity.get("username")));
                participant.put("realName", str(identity.get("realName")));
                participant.put("studentNo", str(identity.get("studentNo")));
                participant.put("className", str(identity.get("className")));
                participant.put("college", str(identity.get("college")));
                if (!str(participant.get("studentNo")).isBlank()) participants.add(participant);
            }
            for (String activityId : stringList(body.get("activityIds"))) {
                Map<String, Object> activity = find(store.activities(), activityId);
                if (activity != null) activityTitles.add(str(activity.get("title")));
            }
        }
        if (participants.isEmpty()) throw Web.badRequest("参与名单为空，请选择已填写身份信息的用户");
        if (title.isBlank()) title = "活动证明";
        String id = "pdf_" + Long.toHexString(System.nanoTime());
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("title", title);
        record.put("source", source);
        record.put("exportId", exportId);
        record.put("activityTitles", activityTitles);
        record.put("participants", participants);
        record.put("participantCount", participants.size());
        record.put("uploaded", false);
        record.put("createdAt", Instant.now().toString());
        record.put("createdBy", admin.username);
        List<Map<String, Object>> records = store.stamped();
        records.add(0, record);
        if (records.size() > 200) records = new ArrayList<>(records.subList(0, 200));
        store.writeStamped(records);
        Map<String, Object> result = stampedSummary(record);
        result.put("participants", participants);
        return result;
    }

    synchronized Map<String, Object> uploadStamped(String id, String filename, byte[] bytes) throws IOException {
        List<Map<String, Object>> records = store.stamped();
        Map<String, Object> record = null;
        for (Map<String, Object> row : records) {
            if (id.equals(str(row.get("id")))) {
                record = row;
                break;
            }
        }
        if (record == null) throw Web.notFound("上传任务不存在");
        if (bytes == null || bytes.length == 0) throw Web.badRequest("请选择 PDF 文件");
        if (bytes.length > 20 * 1024 * 1024) throw Web.badRequest("PDF 文件不能超过 20MB");
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F') {
            throw Web.badRequest("仅支持上传 PDF 文件");
        }
        String name = repairFileName(filename == null ? "" : filename.trim());
        if (name.isBlank()) name = str(record.get("title")) + ".pdf";
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) name = name + ".pdf";
        record.put("filename", safeFileName(name));
        record.put("size", bytes.length);
        record.put("uploaded", true);
        record.put("uploadedAt", Instant.now().toString());
        store.saveStampedPdf(id, bytes);
        store.writeStamped(records);
        return stampedSummary(record);
    }

    Map<String, Object> stampedList() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> record : store.stamped()) {
            if (bool(record.get("uploaded"), false)) rows.add(stampedSummary(record));
        }
        rows.sort(Comparator.comparing((Map<String, Object> row) -> str(row.get("uploadedAt"))).reversed());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows);
        result.put("total", rows.size());
        return result;
    }

    synchronized void deleteStamped(String id) {
        List<Map<String, Object>> records = store.stamped();
        boolean removed = records.removeIf(record -> id.equals(str(record.get("id"))));
        if (!removed) throw Web.notFound("盖章文件不存在");
        store.writeStamped(records);
        store.deleteStampedPdf(id);
    }

    byte[] stampedFile(String id) throws IOException {
        Map<String, Object> record = findStamped(id);
        if (record == null || !bool(record.get("uploaded"), false)) throw Web.notFound("盖章文件不存在");
        byte[] bytes = store.stampedPdf(id);
        if (bytes == null) throw Web.notFound("盖章文件不存在");
        return bytes;
    }

    String stampedFilename(String id) {
        Map<String, Object> record = findStamped(id);
        return record == null ? "盖章证明.pdf" : str(record.get("filename"));
    }

    /** 参与者可见的盖章证明：按用户 ID 或身份表学号匹配名单。 */
    Map<String, Object> myStamped(AuthUser user) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> record : store.stamped()) {
            if (!bool(record.get("uploaded"), false)) continue;
            if (matchesParticipant(record, user)) rows.add(stampedSummary(record));
        }
        rows.sort(Comparator.comparing((Map<String, Object> row) -> str(row.get("uploadedAt"))).reversed());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows);
        result.put("total", rows.size());
        return result;
    }

    byte[] myStampedFile(AuthUser user, String id) throws IOException {
        Map<String, Object> record = findStamped(id);
        if (record == null || !bool(record.get("uploaded"), false) || !matchesParticipant(record, user)) {
            throw Web.notFound("盖章文件不存在");
        }
        return stampedFile(id);
    }

    private boolean matchesParticipant(Map<String, Object> record, AuthUser user) {
        Map<String, Object> identity = store.identities().get(user.id);
        String studentNo = identity == null ? "" : str(identity.get("studentNo"));
        Object participants = record.get("participants");
        if (!(participants instanceof List<?> list)) return false;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> participant)) continue;
            if (user.id.equals(str(participant.get("userId")))) return true;
            if (!studentNo.isBlank() && studentNo.equals(str(participant.get("studentNo")))) return true;
        }
        return false;
    }

    private Map<String, Object> findStamped(String id) {
        if (id == null || !id.matches("pdf_[0-9a-f]+")) return null;
        for (Map<String, Object> record : store.stamped()) {
            if (id.equals(str(record.get("id")))) return record;
        }
        return null;
    }

    private Map<String, Object> findExport(String id) {
        for (Map<String, Object> record : store.exports()) {
            if (id.equals(str(record.get("id")))) return record;
        }
        return null;
    }

    private Map<String, Object> stampedSummary(Map<String, Object> record) {
        Map<String, Object> row = new LinkedHashMap<>(record);
        row.remove("participants");
        return row;
    }

    // ---------------------------------------------------------------- 辅助

    static Eligibility eligibility(Object studentNoValue, Object graduationYearValue) {
        String studentNo = str(studentNoValue).trim();
        if (studentNo.isEmpty()) return new Eligibility("NONE", 0, "未填写", false, 0, 0);
        if (!studentNo.matches("^51\\d{8}$")) {
            return new Eligibility("INVALID", 0, "学号格式无效", false, 0, 0);
        }
        int enrollmentYear = Integer.parseInt(studentNo.substring(2, 6));
        LocalDate now = LocalDate.now();
        int academicYear = now.getMonthValue() >= 9 ? now.getYear() : now.getYear() - 1;
        if (enrollmentYear < 2000 || enrollmentYear > now.getYear()) {
            return new Eligibility("INVALID", 0, "学号年份无效", false, enrollmentYear, 0);
        }
        int graduationYear = number(graduationYearValue, enrollmentYear + 4);
        if (graduationYear < enrollmentYear || graduationYear > enrollmentYear + 10) {
            graduationYear = enrollmentYear + 4;
        }
        int grade = academicYear - enrollmentYear + 1;
        // 默认参与只覆盖大一至大三；大四起需手动“继续参与”，毕业后同样默认退出
        LocalDate cutoff = LocalDate.of(graduationYear, 8, 31);
        if (now.isAfter(cutoff)) {
            return new Eligibility("GRADUATED", grade, "已毕业", false, enrollmentYear, graduationYear);
        }
        if (grade < 1) {
            return new Eligibility("INVALID", 0, "学号年份无效", false, enrollmentYear, graduationYear);
        }
        if (grade >= 4) {
            return new Eligibility("SENIOR", grade, "大四", false, enrollmentYear, graduationYear);
        }
        String label = switch (grade) {
            case 1 -> "大一";
            case 2 -> "大二";
            default -> "大三";
        };
        return new Eligibility("ELIGIBLE", grade, label, true, enrollmentYear, graduationYear);
    }

    private String exclusionReason(boolean optIn, boolean continueParticipate, Map<String, Object> row,
                                    Eligibility eligibility) {
        if (!optIn) return "未开启活动证明";
        if (!eligibility.proofAllowed() && !continueParticipate) {
            return ("SENIOR".equals(eligibility.status()) ? "大四" : "已毕业") + "，已过默认参与时间段";
        }
        if (str(row.get("realName")).isBlank()) return "未填写姓名";
        if (str(row.get("studentNo")).isBlank()) return "未填写学号";
        if (str(row.get("className")).isBlank()) return "未填写专业班级";
        if ("INVALID".equals(eligibility.status())) return eligibility.label();
        return null;
    }

    private Map<String, String> proofValues(Map<String, Object> row, String proofNo, String issueDate, String issuer) {
        List<String> activities = stringList(row.get("activities"));
        Map<String, String> values = new LinkedHashMap<>();
        put(values, "name", str(row.get("realName")));
        put(values, "studentNo", str(row.get("studentNo")));
        put(values, "college", str(row.get("college")));
        put(values, "className", str(row.get("className")));
        put(values, "major", str(row.get("major")));
        put(values, "username", str(row.get("username")));
        put(values, "activities", String.join("、", activities));
        put(values, "activityCount", String.valueOf(activities.size()));
        Eligibility eligibility = eligibility(row.get("studentNo"), row.get("graduationYear"));
        put(values, "enrollmentYear", String.valueOf(eligibility.enrollmentYear()));
        put(values, "graduationYear", String.valueOf(eligibility.graduationYear()));
        put(values, "proofNo", proofNo);
        put(values, "issueDate", issueDate);
        put(values, "issuer", issuer);
        put(values, "generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return values;
    }

    private static void put(Map<String, String> values, String key, String value) {
        values.put(key, value == null ? "" : value);
        String snake = key.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT);
        if (!snake.equals(key)) values.put(snake, value == null ? "" : value);
    }

    private List<Map<String, Object>> selectedActivities(List<String> ids) {
        if (ids == null || ids.isEmpty()) throw Web.badRequest("请至少选择一个活动");
        List<Map<String, Object>> activities = new ArrayList<>();
        for (String id : new LinkedHashSet<>(ids)) {
            Map<String, Object> activity = find(store.activities(), id);
            if (activity == null) throw Web.badRequest("活动不存在：" + id);
            activities.add(activity);
        }
        activities.sort(Comparator.comparing(activity -> str(activity.get("startTime"))));
        return activities;
    }

    /** userId → 通过核验的活动 id 集合（仅统计所选活动）。 */
    private Map<String, Set<String>> passedActivities(List<Map<String, Object>> activities) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> byUser = new LinkedHashMap<>();
        for (Map<String, Object> participation : store.participations()) {
            if (!"JOINED".equals(str(participation.get("status")))) continue;
            if (!VERIFY_PASSED.equals(str(participation.get("verify")))) continue;
            byUser.computeIfAbsent(str(participation.get("userId")), key -> new ArrayList<>()).add(participation);
        }
        for (Map.Entry<String, List<Map<String, Object>>> entry : byUser.entrySet()) {
            Set<String> ids = new LinkedHashSet<>();
            for (Map<String, Object> participation : entry.getValue()) {
                String activityId = str(participation.get("activityId"));
                boolean selected = activities.stream().anyMatch(activity -> activityId.equals(str(activity.get("id"))));
                if (selected) ids.add(activityId);
            }
            if (!ids.isEmpty()) result.put(entry.getKey(), ids);
        }
        return result;
    }

    private ParticipationUser participationUser(String userId) {
        for (Map<String, Object> row : store.participations()) {
            if (userId.equals(str(row.get("userId")))) {
                return new ParticipationUser(str(row.get("username")), str(row.get("nickname")));
            }
        }
        return new ParticipationUser("", "");
    }

    private record ParticipationUser(String username, String nickname) {
    }

    private Map<String, Object> identityView(AuthUser user, Map<String, Object> identity) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.id);
        result.put("username", user.username);
        result.put("proofOptIn", identity != null && bool(identity.get("proofOptIn"), false));
        result.put("continueParticipate", identity != null && bool(identity.get("continueParticipate"), false));
        result.put("realName", identity == null ? "" : str(identity.get("realName")));
        result.put("studentNo", identity == null ? "" : str(identity.get("studentNo")));
        result.put("college", identity == null ? "" : str(identity.get("college")));
        result.put("className", identity == null ? "" : str(identity.get("className")));
        result.put("major", identity == null ? "" : str(identity.get("major")));
        result.put("graduationYear", identity == null ? null : identity.get("graduationYear"));
        result.put("updatedAt", identity == null ? "" : str(identity.get("updatedAt")));
        Eligibility eligibility = eligibility(result.get("studentNo"), result.get("graduationYear"));
        result.put("eligibility", eligibility.status());
        result.put("grade", eligibility.grade());
        result.put("gradeLabel", eligibility.label());
        result.put("defaultAllowed", eligibility.proofAllowed());
        result.put("proofAllowed", eligibility.proofAllowed()
                || (identity != null && bool(identity.get("continueParticipate"), false)));
        result.put("enrollmentYear", eligibility.enrollmentYear());
        result.put("graduationYear", eligibility.graduationYear());
        return result;
    }

    private Map<String, Object> publicRow(Map<String, Object> activity, AuthUser user) {
        Map<String, Object> row = new LinkedHashMap<>(activity);
        String id = str(activity.get("id"));
        int joined = joinedCount(id);
        row.put("joinedCount", joined);
        row.put("passedCount", passedCount(id));
        row.put("signupOpen", "OPEN".equals(str(activity.get("status")))
                && signupOpen(activity, joined));
        row.put("spotsLeft", number(activity.get("capacity"), 0) > 0
                ? Math.max(0, number(activity.get("capacity"), 0) - joined) : -1);
        if (user != null) row.put("mine", participation(id, user.id));
        return row;
    }

    private Map<String, Object> participationView(Map<String, Object> activity, Map<String, Object> participation) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("activityId", str(activity.get("id")));
        row.put("title", str(activity.get("title")));
        row.put("summary", str(activity.get("summary")));
        row.put("location", str(activity.get("location")));
        row.put("startTime", str(activity.get("startTime")));
        row.put("endTime", str(activity.get("endTime")));
        row.put("status", str(participation.get("status")));
        row.put("verify", str(participation.get("verify")));
        row.put("verifyNote", str(participation.get("verifyNote")));
        row.put("source", str(participation.get("source")));
        row.put("joinedAt", str(participation.get("joinedAt")));
        row.put("updatedAt", str(participation.get("updatedAt")));
        row.put("userId", str(participation.get("userId")));
        row.put("username", str(participation.get("username")));
        row.put("nickname", str(participation.get("nickname")));
        return row;
    }

    private Map<String, Object> participation(String activityId, String userId) {
        Map<String, Object> row = findParticipation(store.participations(), activityId, userId);
        if (row == null) return null;
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("status", str(row.get("status")));
        view.put("verify", str(row.get("verify")));
        view.put("verifyNote", str(row.get("verifyNote")));
        view.put("joinedAt", str(row.get("joinedAt")));
        return view;
    }

    private Map<String, Object> stats(String activityId) {
        int joined = 0;
        int pending = 0;
        int passed = 0;
        int rejected = 0;
        for (Map<String, Object> row : store.participations()) {
            if (!activityId.equals(str(row.get("activityId")))) continue;
            if (!"JOINED".equals(str(row.get("status")))) continue;
            joined++;
            String verify = str(row.get("verify"));
            if (VERIFY_PENDING.equals(verify)) pending++;
            else if (VERIFY_PASSED.equals(verify)) passed++;
            else if (VERIFY_REJECTED.equals(verify)) rejected++;
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("joined", joined);
        stats.put("pending", pending);
        stats.put("passed", passed);
        stats.put("rejected", rejected);
        return stats;
    }

    private int joinedCount(String activityId) {
        int count = 0;
        for (Map<String, Object> row : store.participations()) {
            if (activityId.equals(str(row.get("activityId"))) && "JOINED".equals(str(row.get("status")))) count++;
        }
        return count;
    }

    private int passedCount(String activityId) {
        int count = 0;
        for (Map<String, Object> row : store.participations()) {
            if (activityId.equals(str(row.get("activityId"))) && "JOINED".equals(str(row.get("status")))
                    && VERIFY_PASSED.equals(str(row.get("verify")))) count++;
        }
        return count;
    }

    private static boolean signupOpen(Map<String, Object> activity, int joined) {
        if (!"OPEN".equals(str(activity.get("status")))) return false;
        String now = LocalDateTime.now().withSecond(0).withNano(0).toString();
        String signupStart = str(activity.get("signupStart"));
        String signupEnd = str(activity.get("signupEnd"));
        if (!signupStart.isEmpty() && now.compareTo(signupStart) < 0) return false;
        if (!signupEnd.isEmpty() && now.compareTo(signupEnd) > 0) return false;
        int capacity = number(activity.get("capacity"), 0);
        return capacity <= 0 || joined < capacity;
    }

    private void ensurePublished(Map<String, Object> activity) {
        if (!"OPEN".equals(str(activity.get("status")))) throw Web.badRequest("活动尚未开放报名");
    }

    private Map<String, Object> requireActivity(String id) {
        if (id == null || id.isBlank()) throw Web.badRequest("缺少活动 ID");
        Map<String, Object> activity = find(store.activities(), id);
        if (activity == null) throw Web.notFound("活动不存在");
        return activity;
    }

    private static Map<String, Object> find(List<Map<String, Object>> activities, String id) {
        for (Map<String, Object> activity : activities) {
            if (id.equals(str(activity.get("id")))) return activity;
        }
        return null;
    }

    private static Map<String, Object> findParticipation(List<Map<String, Object>> rows, String activityId,
                                                         String userId) {
        for (Map<String, Object> row : rows) {
            if (activityId.equals(str(row.get("activityId"))) && userId.equals(str(row.get("userId")))) return row;
        }
        return null;
    }

    private static List<Map<String, Object>> sorted(List<Map<String, Object>> activities) {
        List<Map<String, Object>> rows = new ArrayList<>(activities);
        rows.sort(Comparator.comparing((Map<String, Object> activity) -> str(activity.get("startTime")))
                .reversed());
        return rows;
    }

    private static Map<String, Object> page(List<Map<String, Object>> rows, int page, int size) {
        int current = Math.max(1, page);
        int capped = Math.min(Math.max(1, size), 200);
        int from = Math.min((current - 1) * capped, rows.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", rows.stream().skip(from).limit(capped).toList());
        result.put("total", rows.size());
        result.put("page", current);
        result.put("size", capped);
        return result;
    }

    private static Map<String, Object> exportSummary(Map<String, Object> record) {
        Map<String, Object> row = new LinkedHashMap<>(record);
        row.remove("snapshots");
        return row;
    }

    /** 修复历史上传时把 UTF-8 字节按 Latin-1 解释导致的乱码文件名。 */
    static String repairFileName(String name) {
        if (name == null || name.isBlank()) return "";
        boolean highLatin = name.chars().anyMatch(c -> c > 0x7F && c <= 0xFF);
        boolean allLatin = name.chars().allMatch(c -> c <= 0xFF);
        if (!highLatin || !allLatin) return name;
        try {
            String repaired = new String(name.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),
                    java.nio.charset.StandardCharsets.UTF_8);
            return repaired.contains("\uFFFD") ? name : repaired;
        } catch (Exception e) {
            return name;
        }
    }

    static String safeFileName(String value) {
        String name = value == null ? "file" : value.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        return name.isEmpty() ? "file" : name;
    }

    private static String required(Object value, String label, int max) {
        String text = str(value).trim();
        if (text.isEmpty()) throw Web.badRequest("请填写" + label);
        if (text.length() > max) throw Web.badRequest(label + "不能超过 " + max + " 个字符");
        return text;
    }

    private static String optional(Object value, int max) {
        String text = str(value).trim();
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static String time(Object value) {
        String text = str(value).trim();
        if (text.isEmpty()) return "";
        if (!text.matches("\\d{4}-\\d{2}-\\d{2}(T\\d{2}:\\d{2}(:\\d{2})?)?")) {
            throw Web.badRequest("时间格式不正确");
        }
        return text;
    }

    private static String lower(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    static int number(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        if (value == null) return fallback;
        String text = String.valueOf(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    static List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) result.add(String.valueOf(item).trim());
            }
        } else if (value instanceof String text && !text.isBlank()) {
            for (String item : text.split(",")) {
                if (!item.isBlank()) result.add(item.trim());
            }
        }
        return result;
    }
}
