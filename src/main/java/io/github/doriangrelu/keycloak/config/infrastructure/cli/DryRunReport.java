/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2026 keycloak-config-cli contributors
 * ---
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---license-end
 */

package io.github.doriangrelu.keycloak.config.infrastructure.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.doriangrelu.keycloak.config.application.reconcile.PlannedChange;
import io.github.doriangrelu.keycloak.config.domain.common.UnmodeledAttributes;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The report of a dry run (EPIC-004): by realm, the resources to create, update and delete, with the fields an update
 * changes. Field values are never shown, so that no secret ends up in a log.
 */
public final class DryRunReport {

    /** A change of the report. */
    record Entry(String type, String label, String key, String change, List<String> fields) {}

    /** The changes of one realm. */
    record RealmEntry(String realm, List<Entry> changes) {}

    private final List<RealmEntry> realms;

    private DryRunReport(final List<RealmEntry> realms) {
        this.realms = realms;
    }

    /** @param plans changes by realm, as the reconciliation planned them */
    public static DryRunReport of(final Map<String, List<PlannedChange>> plans) {
        List<RealmEntry> realms = new ArrayList<>();
        plans.forEach((realm, changes) -> realms.add(new RealmEntry(
                realm, changes.stream().map(DryRunReport::entryOf).toList())));
        return new DryRunReport(realms);
    }

    private static Entry entryOf(final PlannedChange change) {
        return new Entry(
                change.type().name(),
                change.type().label(),
                String.valueOf(change.key()),
                change.kind().name().toLowerCase(Locale.ROOT),
                change.kind() == PlannedChange.Kind.UPDATE
                        ? changedFields(change.before(), change.after())
                        : List.of());
    }

    /** Names of the fields that differ; the unmodeled attributes count as fields of their own. */
    static List<String> changedFields(final Object before, final Object after) {
        Map<String, Object> current = fieldsOf(before);
        Map<String, Object> target = fieldsOf(after);
        Set<String> names = new LinkedHashSet<>(target.keySet());
        names.addAll(current.keySet());
        return names.stream()
                .filter(name -> !Objects.equals(current.get(name), target.get(name)))
                .toList();
    }

    private static Map<String, Object> fieldsOf(final Object resource) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (resource == null || !resource.getClass().isRecord()) {
            return fields;
        }
        for (RecordComponent component : resource.getClass().getRecordComponents()) {
            Object value;
            try {
                value = component.getAccessor().invoke(resource);
            } catch (final ReflectiveOperationException unreadable) {
                throw new IllegalStateException("Cannot read " + component.getName() + " of " + resource, unreadable);
            }
            if (value instanceof UnmodeledAttributes unmodeled) {
                fields.putAll(unmodeled.values());
            } else {
                fields.put(component.getName(), value);
            }
        }
        return fields;
    }

    public int pendingChanges() {
        return this.realms.stream().mapToInt(realm -> realm.changes().size()).sum();
    }

    /** Report for people. */
    public String toText() {
        StringBuilder text = new StringBuilder();
        for (RealmEntry realm : this.realms) {
            text.append(String.format(
                    "Realm '%s': %d change(s)%n", realm.realm(), realm.changes().size()));
            for (Entry change : realm.changes()) {
                text.append(String.format(
                        "  %s %s '%s'%s%n",
                        symbolOf(change.change()),
                        change.label(),
                        change.key(),
                        change.fields().isEmpty() ? "" : " (" + String.join(", ", change.fields()) + ")"));
            }
        }
        int pending = pendingChanges();
        text.append(
                pending == 0
                        ? String.format("Dry run: no change pending.%n")
                        : String.format("Dry run: %d change(s) pending.%n", pending));
        return text.toString();
    }

    private static String symbolOf(final String change) {
        return switch (change) {
            case "create" -> "+";
            case "delete" -> "-";
            default -> "~";
        };
    }

    /** Report for tools, such as a continuous integration. */
    public String toJson() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("pendingChanges", pendingChanges());
        report.put("realms", this.realms);
        try {
            return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(report);
        } catch (final JsonProcessingException unexpected) {
            throw new IllegalStateException("Cannot write the dry run report", unexpected);
        }
    }
}
