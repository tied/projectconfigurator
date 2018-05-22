package ru.mail.jira.plugins.projectconfigurator.customfield;

import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.issue.customfields.impl.AbstractSingleFieldType;
import com.atlassian.jira.issue.customfields.impl.FieldValidationException;
import com.atlassian.jira.issue.customfields.manager.GenericConfigManager;
import com.atlassian.jira.issue.customfields.persistence.CustomFieldValuePersister;
import com.atlassian.jira.issue.customfields.persistence.PersistenceFieldType;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mail.jira.plugins.projectconfigurator.configuration.ProjectConfiguratorManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class ProjectConfigurationCFType extends AbstractSingleFieldType<ProjectConfiguration> {
    private final static Logger log = LoggerFactory.getLogger(ProjectConfigurationCFType.class);

    private final ProjectConfiguratorManager projectConfiguratorManager;
    private final ProjectRoleManager projectRoleManager;
    private final UserManager userManager;

    protected ProjectConfigurationCFType(CustomFieldValuePersister customFieldValuePersister, GenericConfigManager genericConfigManager, ProjectConfiguratorManager projectConfiguratorManager, ProjectRoleManager projectRoleManager, UserManager userManager) {
        super(customFieldValuePersister, genericConfigManager);
        this.projectConfiguratorManager = projectConfiguratorManager;
        this.projectRoleManager = projectRoleManager;
        this.userManager = userManager;
    }

    @Nonnull
    @Override
    protected PersistenceFieldType getDatabaseType() {
        return PersistenceFieldType.TYPE_UNLIMITED_TEXT;
    }

    @Nullable
    @Override
    protected Object getDbValueFromObject(ProjectConfiguration value) {
        return getStringFromSingularObject(value);
    }

    @Nullable
    @Override
    protected ProjectConfiguration getObjectFromDbValue(@Nonnull Object value) throws FieldValidationException {
        return getSingularObjectFromString((String) value);
    }

    @Override
    public String getStringFromSingularObject(ProjectConfiguration value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public ProjectConfiguration getSingularObjectFromString(String value) throws FieldValidationException {
        try {
            return StringUtils.isEmpty(value) ? null : buildValue(value);
        } catch (Exception e) {
            throw new FieldValidationException(e.getMessage());
        }
    }

    private ProjectConfiguration buildValue(String strValue) {
        try {
            if (StringUtils.isEmpty(strValue))
                return null;

            JsonObject jsonProjectConfiguration = new JsonParser().parse(strValue).getAsJsonObject();
            List<ProjectConfiguration.Process> processes = new ArrayList<>();
            for (JsonElement processElement : jsonProjectConfiguration.getAsJsonArray("processes")) {
                JsonObject processObject = processElement.getAsJsonObject();

                ProjectConfiguration.Process role = new ProjectConfiguration().new Process();
                role.setIssueType(projectConfiguratorManager.getIssueType(processObject.getAsJsonPrimitive("issueTypeId").getAsString()));
                role.setJiraWorkflow(projectConfiguratorManager.getWorkflow(processObject.getAsJsonPrimitive("workflowName").getAsString()));
                role.setFieldScreenScheme(projectConfiguratorManager.getFieldScreenScheme(processObject.getAsJsonPrimitive("screenSchemeId").getAsLong()));
                processes.add(role);
            }

            List<ProjectConfiguration.Role> roles = new ArrayList<>();
            for (JsonElement roleElement : jsonProjectConfiguration.getAsJsonArray("roles")) {
                JsonObject roleObject = roleElement.getAsJsonObject();
                List<ApplicationUser> users = new ArrayList<>();
                if (roleObject.has("userKeys"))
                    for (JsonElement userElement : roleObject.getAsJsonArray("userKeys"))
                        users.add(userManager.getUserByKey(userElement.getAsJsonPrimitive().getAsString()));
                List<Group> groups = new ArrayList<>();
                if (roleObject.has("groupNames"))
                    for (JsonElement groupElement : roleObject.getAsJsonArray("groupNames"))
                        groups.add(projectConfiguratorManager.getGroup(groupElement.getAsJsonPrimitive().getAsString()));

                ProjectConfiguration.Role role = new ProjectConfiguration().new Role();
                role.setProjectRole(projectRoleManager.getProjectRole(roleObject.getAsJsonPrimitive("projectRoleId").getAsLong()));
                role.setUsers(users);
                role.setGroups(groups);
                roles.add(role);
            }

            ProjectConfiguration value = new ProjectConfiguration();
            value.setProjectName(jsonProjectConfiguration.get("projectName").getAsString());
            value.setProjectKey(jsonProjectConfiguration.get("projectKey").getAsString());
            value.setProjectLead(userManager.getUserByKey(jsonProjectConfiguration.get("projectLeadKey").getAsString()));
            value.setProcesses(processes);
            value.setRoles(roles);
            value.setPermissionScheme(projectConfiguratorManager.getPermissionScheme(jsonProjectConfiguration.get("permissionSchemeId").getAsLong()));
            value.setNotificationScheme(projectConfiguratorManager.getNotificationScheme(jsonProjectConfiguration.get("notificationSchemeId").getAsLong()));
            return value;
        } catch (FieldValidationException e) {
            throw e;
        } catch (JsonSyntaxException e) {
            String errorMsg = "Bad value => " + strValue;
            log.error(errorMsg, e);
            throw new FieldValidationException(errorMsg);
        } catch (Exception e) {
            String errorMsg = "Error while trying to build value for project configuration picker. String value => " + strValue;
            log.error(errorMsg, e);
            throw new FieldValidationException(e.getMessage());
        }
    }
}