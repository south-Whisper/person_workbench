package com.example.demo.service.workspace;

import com.example.demo.service.organization.EmployeeService;
import com.example.demo.service.talent.TalentService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class WorkspaceService {
    private final TalentService talents;
    private final EmployeeService employees;

    public WorkspaceService(TalentService talents, EmployeeService employees) {
        this.talents = talents;
        this.employees = employees;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> load() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String type : TalentService.TYPES) result.put(type, talents.recordsAcross(type));
        List<Map<String, Object>> people = talents.persons();
        Map<Long, Long> photoByPerson = new LinkedHashMap<>();
        for (Map<String, Object> person : people) {
            Long personId = id(person.get("id"));
            Long assetId = photoAssetId(person);
            if (personId != null && assetId != null) {
                photoByPerson.putIfAbsent(personId, assetId);
            }
        }

        Map<Long, Map<String, Object>> positionById = talents.positions().stream().collect(Collectors.toMap(item -> id(item.get("id")), item -> item, (left, right) -> left));
        for (String type : TalentService.TYPES) {
            Object value = result.get(type);
            if (!(value instanceof List<?> list)) continue;
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> raw)) continue;
                Map<String, Object> item = (Map<String, Object>) raw;
                Map<String, Object> job = positionById.get(id(item.get("jobId")));
                if (job != null) copyCapacity(job, item);
                Long photoAssetId = photoByPerson.get(id(item.get("personId")));
                if (photoAssetId != null) item.put("photoAssetId", photoAssetId);
            }
        }

        List<Map<String, Object>> employmentRows = (List<Map<String, Object>>) result.get("employments");
        List<Map<String, Object>> allEmployees = employees.employees();
        Map<Long, Map<String, Object>> employeeByOnboarding = allEmployees.stream().filter(item -> id(item.get("onboardingId")) != null).collect(Collectors.toMap(item -> id(item.get("onboardingId")), item -> item, (left, right) -> left));
        for (Map<String, Object> employment : employmentRows) {
            Map<String, Object> employee = employeeByOnboarding.get(id(employment.get("onboardingId")));
            if (employee == null) continue;
            employment.put("employeeId", employee.get("id")); employment.put("employeeNumber", employee.get("employeeNumber")); employment.put("employeeRole", employee.get("role"));
            employment.put("username", employee.get("username")); employment.put("hasAccount", employee.get("hasAccount")); employment.put("currentEmployee", employee.get("current"));
        }

        List<Map<String, Object>> employeeRows = allEmployees.stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>(item);
            value.put("personName", item.get("name")); value.put("jobName", text(item, "title").isBlank() ? "员工" : item.get("title"));
            value.put("employeeRole", item.get("role")); value.put("currentEmployee", item.get("current"));
            employmentRows.stream().filter(employment -> Objects.equals(id(employment.get("personId")), id(item.get("personId"))) && Objects.equals(id(employment.get("applicationId")), id(item.get("applicationId")))).findFirst().ifPresent(employment -> value.put("employmentItemId", employment.get("id")));
            return value;
        }).toList();
        result.put("employees", employeeRows);

        result.put("stats", talents.list("", "", "", null, 1, 10).get("stats"));
        result.put("sources", group(people, "source")); result.put("stages", group(people, "status")); result.put("outcomes", group(people, "result")); result.put("jobs", group(people, "job"));
        return result;
    }

    private Map<String, Long> group(List<Map<String, Object>> people, String key) {
        return people.stream().collect(Collectors.groupingBy(person -> text(person, key).isBlank() ? "未填写" : text(person, key), LinkedHashMap::new, Collectors.counting()));
    }

    private void copyCapacity(Map<String, Object> job, Map<String, Object> target) {
        for (String key : List.of("headcount", "occupiedCount", "hiredCount", "remainingCount", "capacityReached")) target.put(key, job.get(key));
    }

    private Long photoAssetId(Map<String, Object> person) {
        if (!(person.get("assets") instanceof List<?> assets)) return null;
        for (Object value : assets) {
            if (!(value instanceof Map<?, ?> asset) || !"证件照".equals(Objects.toString(asset.get("type"), ""))) continue;
            return id(asset.get("id"));
        }
        return null;
    }

    private String text(Map<String, Object> map, String key) { return Objects.toString(map.get(key), ""); }
    private Long id(Object value) { if (value == null || value.toString().isBlank()) return null; try { long number = Long.parseLong(value.toString()); return number > 0 ? number : null; } catch (NumberFormatException ignored) { return null; } }
}
