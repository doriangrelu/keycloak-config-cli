# Example: Groups and Roles

This example demonstrates advanced group hierarchies and role management patterns.

## Use Case

A medium-sized company with:
- Multiple departments and teams
- Role-based access control (RBAC)
- Project-based access
- Manager/employee hierarchies

## Configuration

### File: `01-roles.yaml`

```yaml
realm: enterprise

# ============================================
# REALM ROLES
# ============================================
roles:
  realm:
    # Base roles
    - name: employee
      description: "Base role for all employees"

    - name: manager
      description: "Manager privileges"
      composite: true
      composites:
        realm:
          - employee

    - name: executive
      description: "Executive level access"
      composite: true
      composites:
        realm:
          - manager

    # Functional roles
    - name: can-view-reports
      description: "View company reports"

    - name: can-export-data
      description: "Export data from systems"

    - name: can-manage-users
      description: "Manage user accounts"

    - name: can-approve-expenses
      description: "Approve expense reports"

    # Combined functional roles
    - name: finance-analyst
      description: "Finance team analyst"
      composite: true
      composites:
        realm:
          - employee
          - can-view-reports
          - can-export-data

    - name: hr-manager
      description: "HR manager with user management"
      composite: true
      composites:
        realm:
          - manager
          - can-manage-users

    # Admin roles
    - name: system-admin
      description: "Full system administration"
      composite: true
      composites:
        realm:
          - employee
          - can-manage-users
          - can-view-reports
          - can-export-data
        client:
          realm-management:
            - manage-users
            - view-users
            - manage-clients
```

### File: `02-groups.yaml`

```yaml
realm: enterprise

groups:
  # ============================================
  # ORGANIZATIONAL STRUCTURE
  # ============================================
  - name: organization
    attributes:
      type: ["root"]

    subGroups:
      # Engineering Department
      - name: engineering
        attributes:
          department: ["Engineering"]
          cost-center: ["ENG-001"]
        realmRoles:
          - employee

        subGroups:
          - name: platform
            attributes:
              team: ["Platform"]
            subGroups:
              - name: platform-engineers
                realmRoles:
                  - employee
              - name: platform-leads
                realmRoles:
                  - manager

          - name: product
            attributes:
              team: ["Product"]
            subGroups:
              - name: frontend
                realmRoles:
                  - employee
              - name: backend
                realmRoles:
                  - employee
              - name: product-leads
                realmRoles:
                  - manager

          - name: engineering-management
            realmRoles:
              - executive
              - can-approve-expenses

      # Finance Department
      - name: finance
        attributes:
          department: ["Finance"]
          cost-center: ["FIN-001"]
        realmRoles:
          - employee
          - can-view-reports

        subGroups:
          - name: accounting
            realmRoles:
              - finance-analyst
          - name: financial-planning
            realmRoles:
              - finance-analyst
              - can-export-data
          - name: finance-management
            realmRoles:
              - manager
              - can-approve-expenses

      # Human Resources
      - name: human-resources
        attributes:
          department: ["Human Resources"]
          cost-center: ["HR-001"]
        realmRoles:
          - employee

        subGroups:
          - name: recruiters
            realmRoles:
              - can-view-reports
          - name: hr-partners
            realmRoles:
              - hr-manager
          - name: hr-leadership
            realmRoles:
              - executive
              - can-manage-users

      # Sales Department
      - name: sales
        attributes:
          department: ["Sales"]
          cost-center: ["SAL-001"]
        realmRoles:
          - employee

        subGroups:
          - name: sales-reps
          - name: account-managers
            realmRoles:
              - can-view-reports
          - name: sales-leadership
            realmRoles:
              - manager
              - can-approve-expenses

  # ============================================
  # PROJECT-BASED GROUPS
  # ============================================
  - name: projects
    attributes:
      type: ["projects"]

    subGroups:
      - name: project-alpha
        attributes:
          project-code: ["ALPHA-2024"]
          status: ["active"]
        clientRoles:
          project-portal:
            - project-member

        subGroups:
          - name: alpha-contributors
          - name: alpha-reviewers
            clientRoles:
              project-portal:
                - project-reviewer
          - name: alpha-leads
            clientRoles:
              project-portal:
                - project-admin

      - name: project-beta
        attributes:
          project-code: ["BETA-2024"]
          status: ["active"]
        clientRoles:
          project-portal:
            - project-member

        subGroups:
          - name: beta-contributors
          - name: beta-leads
            clientRoles:
              project-portal:
                - project-admin

  # ============================================
  # ADMINISTRATIVE GROUPS
  # ============================================
  - name: administrators
    attributes:
      type: ["admin"]
    realmRoles:
      - system-admin

    subGroups:
      - name: super-admins
        clientRoles:
          realm-management:
            - realm-admin

      - name: user-admins
        clientRoles:
          realm-management:
            - manage-users
            - view-users

      - name: support-staff
        realmRoles:
          - can-view-reports
        clientRoles:
          realm-management:
            - view-users
```

### File: `03-client-roles.yaml`

```yaml
realm: enterprise

clients:
  - clientId: project-portal
    name: "Project Portal"
    enabled: true
    publicClient: true
    standardFlowEnabled: true

    redirectUris:
      - "https://projects.enterprise.com/*"

roles:
  client:
    project-portal:
      - name: project-member
        description: "Basic project access"
      - name: project-reviewer
        description: "Can review and approve"
      - name: project-admin
        description: "Full project administration"
        composite: true
        composites:
          client:
            project-portal:
              - project-member
              - project-reviewer
```

## Import Order

```bash
# Process files in order
java -jar keycloak-config-cli.jar \
  --import.files.locations=01-roles.yaml,02-groups.yaml,03-client-roles.yaml \
  # ... other options
```

Or use a single directory:
```bash
java -jar keycloak-config-cli.jar \
  --import.files.locations=config/
```

## Resulting Structure

```
organization/
├── engineering/
│   ├── platform/
│   │   ├── platform-engineers  [employee]
│   │   └── platform-leads      [manager]
│   ├── product/
│   │   ├── frontend            [employee]
│   │   ├── backend             [employee]
│   │   └── product-leads       [manager]
│   └── engineering-management  [executive, can-approve-expenses]
├── finance/
│   ├── accounting              [finance-analyst]
│   ├── financial-planning      [finance-analyst, can-export-data]
│   └── finance-management      [manager, can-approve-expenses]
├── human-resources/
│   ├── recruiters              [can-view-reports]
│   ├── hr-partners             [hr-manager]
│   └── hr-leadership           [executive, can-manage-users]
└── sales/
    ├── sales-reps              []
    ├── account-managers        [can-view-reports]
    └── sales-leadership        [manager, can-approve-expenses]

projects/
├── project-alpha/
│   ├── alpha-contributors      [project-member]
│   ├── alpha-reviewers         [project-member, project-reviewer]
│   └── alpha-leads             [project-admin]
└── project-beta/
    ├── beta-contributors       [project-member]
    └── beta-leads              [project-admin]

administrators/
├── super-admins                [system-admin, realm-admin]
├── user-admins                 [system-admin, manage-users]
└── support-staff               [system-admin, can-view-reports]
```

## Assigning Users

```yaml
users:
  # Engineering manager
  - username: alice.johnson
    groups:
      - /organization/engineering/engineering-management
      - /projects/project-alpha/alpha-leads

  # Backend developer on project alpha
  - username: bob.smith
    groups:
      - /organization/engineering/product/backend
      - /projects/project-alpha/alpha-contributors

  # HR partner
  - username: carol.davis
    groups:
      - /organization/human-resources/hr-partners

  # System administrator
  - username: admin.user
    groups:
      - /administrators/super-admins
```

## Full Management Mode

To ensure groups match exactly:

```yaml
import:
  managed:
    group: full
    realm-role: full
    client-role: full
```

## Best Practices Demonstrated

1. **Hierarchical Organization**: Reflects actual company structure
2. **Role Composition**: Complex roles built from simple ones
3. **Separation of Concerns**: Organizational vs project groups
4. **Attribute-Based Access**: Departments and cost centers as attributes
5. **Client-Specific Roles**: Project-specific access control

## See Also

- [Basic Realm Example](basic-realm.md)
- [Groups Configuration](../configuration/groups.md)
- [Roles Configuration](../configuration/roles.md)
