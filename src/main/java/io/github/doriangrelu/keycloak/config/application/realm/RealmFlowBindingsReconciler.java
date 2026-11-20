package io.github.doriangrelu.keycloak.config.application.realm;

import io.github.doriangrelu.keycloak.config.application.realm.port.RealmGateway;
import io.github.doriangrelu.keycloak.config.application.reconcile.RealmContext;
import io.github.doriangrelu.keycloak.config.application.reconcile.Reconciler;
import io.github.doriangrelu.keycloak.config.domain.realm.model.RealmFlowBindings;
import io.github.doriangrelu.keycloak.config.domain.reconcile.ResourceType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** The flows the realm uses, bound once its flows exist. Like the realm settings, the last file wins. */
public final class RealmFlowBindingsReconciler implements Reconciler<String, RealmFlowBindings> {

    private final RealmGateway realms;

    public RealmFlowBindingsReconciler(final RealmGateway realms) {
        this.realms = Objects.requireNonNull(realms, "realms");
    }

    @Override
    public ResourceType type() {
        return ResourceType.REALM_FLOW_BINDING;
    }

    @Override
    public Set<ResourceType> dependsOn() {
        return Set.of(ResourceType.REALM_SETTINGS, ResourceType.AUTHENTICATION_FLOW);
    }

    @Override
    public List<RealmFlowBindings> read(final RealmContext context, final Collection<RealmFlowBindings> desired) {
        return List.of(this.realms.findFlowBindings(context.realm()));
    }

    @Override
    public RealmFlowBindings merge(
            final RealmContext context, final RealmFlowBindings current, final RealmFlowBindings desired) {
        return current.mergedWith(desired);
    }

    @Override
    public void create(final RealmContext context, final RealmFlowBindings resource) {
        this.realms.bindFlows(context.realm(), resource);
    }

    @Override
    public void update(final RealmContext context, final RealmFlowBindings current, final RealmFlowBindings target) {
        this.realms.bindFlows(context.realm(), target);
    }

    @Override
    public void delete(final RealmContext context, final RealmFlowBindings resource) {
        throw new UnsupportedOperationException("The flow bindings of a realm are never deleted");
    }

    @Override
    public boolean isOwnedByKeycloak(final RealmContext context, final RealmFlowBindings resource) {
        return true;
    }

    @Override
    public boolean prunes() {
        return false;
    }

    @Override
    public boolean tracksDeclarations() {
        return false;
    }
}
