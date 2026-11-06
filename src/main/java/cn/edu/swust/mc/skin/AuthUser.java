package cn.edu.swust.mc.skin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AuthUser {
    public String id;
    public String username;
    public String nickname;
    public String email;
    public String phone;
    public String qq;
    public String avatarFileId;
    public boolean emailVerified;
    public String status;
    public String createTime;
    public String updateTime;
    public String lastLoginTime;
    public Object tags = List.of();
    public Object fields = Map.of();
    public List<Map<String, Object>> roles = new ArrayList<>();
    public List<Map<String, Object>> depts = new ArrayList<>();
    public List<String> roleIds = new ArrayList<>();
    public List<String> roleNames = new ArrayList<>();
    public List<String> deptIds = new ArrayList<>();
    public List<String> deptNames = new ArrayList<>();
    public Set<String> permissions = new LinkedHashSet<>();
    public Set<String> rolePermissions = new LinkedHashSet<>();
    public Set<String> extraPermissions = new LinkedHashSet<>();
    public String defaultDeptId;
    public boolean wildcard;
    public boolean admin;
    public int levelWeight;

    public boolean can(String code) {
        return wildcard || permissions.contains("*") || permissions.contains(code);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("username", username);
        map.put("nickname", nickname);
        map.put("email", email);
        map.put("phone", phone);
        map.put("qq", qq);
        map.put("avatar", Avatars.url(avatarFileId));
        map.put("avatarFileId", avatarFileId);
        map.put("emailVerified", emailVerified);
        map.put("status", status);
        map.put("roleIds", roleIds);
        map.put("roleNames", roleNames);
        map.put("roles", roles);
        map.put("deptIds", deptIds);
        map.put("deptNames", deptNames);
        map.put("defaultDeptId", defaultDeptId);
        map.put("tags", tags);
        map.put("fields", fields);
        map.put("permissions", new ArrayList<>(permissions));
        map.put("rolePermissions", new ArrayList<>(rolePermissions));
        map.put("extraPermissions", new ArrayList<>(extraPermissions));
        map.put("wildcard", wildcard);
        map.put("levelWeight", levelWeight);
        map.put("isAdmin", admin);
        map.put("createTime", createTime);
        map.put("updateTime", updateTime);
        map.put("lastLoginTime", lastLoginTime);
        return map;
    }
}
