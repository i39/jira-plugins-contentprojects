package ru.mail.jira.plugins.contentprojects.extras;

import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.issue.link.IssueLinkService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.search.SearchProvider;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.xsrf.RequiresXsrfCheck;
import com.atlassian.jira.util.json.JSONObject;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.query.Query;
import org.apache.commons.lang3.StringUtils;
import ru.mail.jira.plugins.commons.CommonUtils;
import ru.mail.jira.plugins.commons.LocalUtils;
import ru.mail.jira.plugins.commons.RestExecutor;
import ru.mail.jira.plugins.contentprojects.authors.Author;
import ru.mail.jira.plugins.contentprojects.authors.FreelancerAuthor;
import ru.mail.jira.plugins.contentprojects.authors.freelancers.Freelancer;
import ru.mail.jira.plugins.contentprojects.common.Consts;
import ru.mail.jira.plugins.contentprojects.configuration.PluginData;
import ru.mail.jira.plugins.contentprojects.gadgets.RemainingBudgetResource;
import webwork.action.ServletActionContext;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Path("/createActs")
@Produces({MediaType.APPLICATION_JSON})
public class ContentProjectsCreateActsAction extends JiraWebActionSupport {
    private static final String PAYMENT_ACT = "\u0410\u043A\u0442 \u043E\u043F\u043B\u0430\u0442\u044B"; //Акт оплаты
    private final IssueLinkService issueLinkService;
    private final IssueService issueService;
    private final OptionsManager optionsManager;
    private final PluginData pluginData;
    private final ProjectManager projectManager;
    private final SearchProvider searchProvider;
    private final SearchService searchService;

    private long optionId;
    private long[] projectIds;

    private Option option;
    private Set<Project> projects;
    private List<Date> availableActDates;
    private List<Date> availableAnnexDates;

    public ContentProjectsCreateActsAction(IssueLinkService issueLinkService, IssueService issueService, OptionsManager optionsManager, PluginData pluginData, ProjectManager projectManager, SearchProvider searchProvider, SearchService searchService) {
        this.issueLinkService = issueLinkService;
        this.issueService = issueService;
        this.optionsManager = optionsManager;
        this.pluginData = pluginData;
        this.projectManager = projectManager;
        this.searchProvider = searchProvider;
        this.searchService = searchService;
    }

    private boolean isUserAllowed() {
        return CommonUtils.isUserInGroups(getLoggedInApplicationUser(), Consts.ACCOUNTANTS_GROUPS);
    }

    private String sendError(int code) throws IOException {
        if (ServletActionContext.getResponse() != null)
            ServletActionContext.getResponse().sendError(code);
        return NONE;
    }

    @Override
    public String doDefault() throws Exception {
        if (!isUserAllowed())
            return sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return INPUT;
    }

    @Override
    protected void doValidation() {
        option = optionsManager.findByOptionId(optionId);
        if (option == null)
            addError("optionId", getText("issue.field.required", getPaymentMonthFieldName()));

        projects = new HashSet<Project>();
        if (projectIds != null)
            for (long projectId : projectIds)
                if (Consts.PROJECT_IDS.contains(projectId))
                    projects.add(projectManager.getProjectObj(projectId));
        if (projects.isEmpty())
            addError("projectIds", getText("issue.field.required", getText("common.concepts.projects")));
    }

    private void buildAvailableDates() throws ParseException {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(LocalUtils.updateMonthNames(new SimpleDateFormat("MMMM yyyy"), LocalUtils.MONTH_NAMES_NOMINATIVE).parse(option.getValue()));

        availableActDates = new ArrayList<Date>();
        availableAnnexDates = new ArrayList<Date>();
        for (int day = 1; day <= 31; day++) {
            calendar.set(Calendar.DAY_OF_MONTH, day);

            if (calendar.get(Calendar.DAY_OF_MONTH) != day)
                break;
            if (LocalUtils.isWeekend(calendar) || LocalUtils.isHoliday(calendar))
                continue;

            if (day <= 15)
                availableAnnexDates.add(calendar.getTime());
            if (day >= 15)
                availableActDates.add(calendar.getTime());
        }
    }

    class CollectedFreelancerData {
        Collection<String> issueKeys = new ArrayList<String>();
        Collection<String> issueSummaries = new ArrayList<String>();
        Collection<String> issueDescriptions = new ArrayList<String>();
        double totalCost = 0;
        int totalImages = 0;
    }

    private JSONObject getArticleContractJson(Freelancer freelancer, CollectedFreelancerData collectedFreelancerData, Date paymentActDate, Project project) throws Exception {
        final DateFormat DAY_OF_THE_MONTH_FORMAT = new SimpleDateFormat("dd");
        final DateFormat MONTH_FORMAT = LocalUtils.updateMonthNames(new SimpleDateFormat("MMMM"), LocalUtils.MONTH_NAMES_GENITIVE);
        final DateFormat YEAR_FORMAT = new SimpleDateFormat("yy");
        JSONObject json = new JSONObject();
        json.put("templateIds", Collections.<Object>singleton(Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_ARTICLE_TEMPLATE_ID));
        json.put("variableValues", Arrays.<Object>asList(
                DAY_OF_THE_MONTH_FORMAT.format(freelancer.getContractDate()),
                MONTH_FORMAT.format(freelancer.getContractDate()),
                YEAR_FORMAT.format(freelancer.getContractDate()),
                DAY_OF_THE_MONTH_FORMAT.format(paymentActDate.getTime()),
                MONTH_FORMAT.format(paymentActDate.getTime()),
                YEAR_FORMAT.format(paymentActDate.getTime()),
                collectedFreelancerData.issueSummaries.size(),
                StringUtils.join(collectedFreelancerData.issueSummaries, "\n"),
                String.format(new Locale("ru"), "%,d", (int) collectedFreelancerData.totalCost),
                LocalUtils.numberToCaption((int) collectedFreelancerData.totalCost),
                String.format(new Locale("ru"), "%02d", (int) (collectedFreelancerData.totalCost * 100 % 100)),
                StringUtils.isNotEmpty(freelancer.getPayeeName()) ? freelancer.getPayeeName() : freelancer.getFullName(),
                project.getName().substring(4)
        ));
        return json;
    }

    private JSONObject getImagesContractJson(Freelancer freelancer, CollectedFreelancerData collectedFreelancerData, Date paymentActDate, Project project) throws Exception {
        final DateFormat DAY_OF_THE_MONTH_FORMAT = new SimpleDateFormat("dd");
        final DateFormat MONTH_FORMAT = LocalUtils.updateMonthNames(new SimpleDateFormat("MMMM"), LocalUtils.MONTH_NAMES_GENITIVE);
        final DateFormat YEAR_FORMAT = new SimpleDateFormat("yy");
        JSONObject json = new JSONObject();
        json.put("templateIds", Collections.<Object>singleton(Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_IMAGE_TEMPLATE_ID));
        json.put("variableValues", Arrays.<Object>asList(
                DAY_OF_THE_MONTH_FORMAT.format(freelancer.getContractDate()),
                MONTH_FORMAT.format(freelancer.getContractDate()),
                YEAR_FORMAT.format(freelancer.getContractDate()),
                DAY_OF_THE_MONTH_FORMAT.format(paymentActDate.getTime()),
                MONTH_FORMAT.format(paymentActDate.getTime()),
                YEAR_FORMAT.format(paymentActDate.getTime()),
                String.format(new Locale("ru"), "%,d", collectedFreelancerData.totalImages),
                StringUtils.join(collectedFreelancerData.issueDescriptions, "\n"),
                String.format(new Locale("ru"), "%,d", (int) collectedFreelancerData.totalCost),
                LocalUtils.numberToCaption((int) collectedFreelancerData.totalCost),
                String.format(new Locale("ru"), "%02d", (int) (collectedFreelancerData.totalCost * 100 % 100)),
                StringUtils.isNotEmpty(freelancer.getPayeeName()) ? freelancer.getPayeeName() : freelancer.getFullName(),
                project.getName().substring(4)
        ));
        return json;
    }

    private JSONObject getCustomOrderContractJson(Freelancer freelancer, CollectedFreelancerData collectedFreelancerData, Date paymentActDate, Date paymentAnnexDate, Project project) throws Exception {
        final DateFormat DAY_OF_THE_MONTH_FORMAT = new SimpleDateFormat("dd");
        final DateFormat MONTH_FORMAT = LocalUtils.updateMonthNames(new SimpleDateFormat("MMMM"), LocalUtils.MONTH_NAMES_GENITIVE);
        final DateFormat YEAR_FORMAT = new SimpleDateFormat("yy");
        final DateFormat DATE_FORMAT = LocalUtils.updateMonthNames(new SimpleDateFormat("dd MMMM yyyy"), LocalUtils.MONTH_NAMES_GENITIVE);
        JSONObject json = new JSONObject();
        json.put("templateIds", Collections.<Object>singleton(Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_CUSTOM_ORDER_TEMPLATE_ID));
        json.put("variableValues", Arrays.<Object>asList(
                DAY_OF_THE_MONTH_FORMAT.format(freelancer.getContractDate()),
                MONTH_FORMAT.format(freelancer.getContractDate()),
                YEAR_FORMAT.format(freelancer.getContractDate()),
                DATE_FORMAT.format(paymentAnnexDate.getTime()),
                StringUtils.isNotEmpty(freelancer.getPayeeName()) ? freelancer.getPayeeName() : freelancer.getFullName(),
                project.getName().substring(4),
                freelancer.getWorkNames(),
                String.format(new Locale("ru"), "%,d", (int) collectedFreelancerData.totalCost),
                DATE_FORMAT.format(paymentActDate.getTime())
        ));
        return json;
    }

    private JSONObject getContractorContractJson(Freelancer freelancer, CollectedFreelancerData collectedFreelancerData, Date paymentActDate, Date paymentAnnexDate, Project project) throws Exception {
        final DateFormat DAY_OF_THE_MONTH_FORMAT = new SimpleDateFormat("dd");
        final DateFormat MONTH_FORMAT = LocalUtils.updateMonthNames(new SimpleDateFormat("MMMM"), LocalUtils.MONTH_NAMES_GENITIVE);
        final DateFormat YEAR_FORMAT = new SimpleDateFormat("yy");
        JSONObject json = new JSONObject();
        json.put("templateIds", Collections.<Object>singleton(Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_CONTRACTOR_TEMPLATE_ID));
        json.put("variableValues", Arrays.<Object>asList(
                DAY_OF_THE_MONTH_FORMAT.format(freelancer.getContractDate()),
                MONTH_FORMAT.format(freelancer.getContractDate()),
                YEAR_FORMAT.format(freelancer.getContractDate()),
                DAY_OF_THE_MONTH_FORMAT.format(paymentAnnexDate.getTime()),
                MONTH_FORMAT.format(paymentAnnexDate.getTime()),
                YEAR_FORMAT.format(paymentAnnexDate.getTime()),
                StringUtils.isNotEmpty(freelancer.getPayeeName()) ? freelancer.getPayeeName() : freelancer.getFullName(),
                DAY_OF_THE_MONTH_FORMAT.format(paymentActDate.getTime()),
                MONTH_FORMAT.format(paymentActDate.getTime()),
                YEAR_FORMAT.format(paymentActDate.getTime()),
                freelancer.getWorkNames(),
                String.format(new Locale("ru"), "%,d", (int) collectedFreelancerData.totalCost),
                project.getName().substring(4)
        ));
        return json;
    }

    class CollectedPreparedIssueData {
        IssueService.CreateValidationResult createValidationResult;
        Freelancer freelancer;
        CollectedFreelancerData collectedFreelancerData;

        public CollectedPreparedIssueData(IssueService.CreateValidationResult createValidationResult, Freelancer freelancer, CollectedFreelancerData collectedFreelancerData) {
            this.createValidationResult = createValidationResult;
            this.freelancer = freelancer;
            this.collectedFreelancerData = collectedFreelancerData;
        }
    }

    private Collection<CollectedPreparedIssueData> prepareIssues() throws Exception {
        CustomField paymentMonthCf = CommonUtils.getCustomField(Consts.PAYMENT_MONTH_CF_ID);
        CustomField costCf = CommonUtils.getCustomField(Consts.COST_CF_ID);
        CustomField numberImagesCf = CommonUtils.getCustomField(Consts.IMAGES_NUMBER_CF_ID);
        CustomField textAuthorCf = CommonUtils.getCustomField(Consts.TEXT_AUTHOR_CF_ID);

        Collection<CollectedPreparedIssueData> result = new ArrayList<CollectedPreparedIssueData>();

        buildAvailableDates();
        Iterator<Date> possibleActDatesIterator = availableActDates.iterator();
        Iterator<Date> possibleAnnexDatesIterator = availableAnnexDates.iterator();

        for (Project project : projects) {
            for (String contractTypeId: Arrays.asList(Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_ARTICLE_TYPE_ID, Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_IMAGE_TYPE_ID, Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_CUSTOM_ORDER_TYPE_ID, Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_CONTRACTOR_TYPE_ID)) {
                Query query = JqlQueryBuilder.newClauseBuilder().project(project.getId()).and().issueType(contractTypeId).buildQuery();
                SearchResults searchResults = searchProvider.search(query, getLoggedInApplicationUser(), PagerFilter.getUnlimitedFilter());

                Map<Freelancer, CollectedFreelancerData> freelancerDataMap = new HashMap<Freelancer, CollectedFreelancerData>();
                for (Issue issue : searchResults.getIssues()) {
                    if (!option.equals(issue.getCustomFieldValue(paymentMonthCf)))
                        continue;

                    if (!Consts.STATUS_SPENT_IDS.contains(issue.getStatusObject().getId())) {
                        addErrorMessage(getText("ru.mail.jira.plugins.contentprojects.extras.createActs.error.illegalStatus", issue.getKey()));
                        continue;
                    }

                    Author author = (Author) issue.getCustomFieldValue(textAuthorCf);
                    if (!(author instanceof FreelancerAuthor)) {
                        addErrorMessage(getText("ru.mail.jira.plugins.contentprojects.extras.createActs.error.illegalTextAuthor", issue.getKey()));
                        continue;
                    }

                    Freelancer freelancer = ((FreelancerAuthor) author).getFreelancer();
                    CollectedFreelancerData collectedFreelancerData = freelancerDataMap.get(freelancer);
                    if (collectedFreelancerData == null) {
                        collectedFreelancerData = new CollectedFreelancerData();
                        freelancerDataMap.put(freelancer, collectedFreelancerData);
                    }
                    collectedFreelancerData.issueKeys.add(issue.getKey());
                    collectedFreelancerData.issueSummaries.add(issue.getSummary());
                    if (issue.getDescription() != null)
                        collectedFreelancerData.issueDescriptions.add(issue.getDescription());
                    collectedFreelancerData.totalCost += (Double) issue.getCustomFieldValue(costCf);
                    if (issue.getCustomFieldValue(numberImagesCf) != null)
                        collectedFreelancerData.totalImages += ((Double) issue.getCustomFieldValue(numberImagesCf)).intValue();
                }

                for (Map.Entry<Freelancer, CollectedFreelancerData> e : freelancerDataMap.entrySet()) {
                    Date paymentActDate = possibleActDatesIterator.next();
                    if (!possibleActDatesIterator.hasNext())
                        possibleActDatesIterator = availableActDates.iterator();

                    Date paymentAnnexDate = possibleAnnexDatesIterator.next();
                    if (!possibleAnnexDatesIterator.hasNext())
                        possibleAnnexDatesIterator = availableAnnexDates.iterator();

                    JSONObject json;
                    if (contractTypeId.equals(Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_ARTICLE_TYPE_ID))
                        json = getArticleContractJson(e.getKey(), e.getValue(), paymentActDate, project);
                    else if (contractTypeId.equals(Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_IMAGE_TYPE_ID))
                        json = getImagesContractJson(e.getKey(), e.getValue(), paymentActDate, project);
                    else if (contractTypeId.equals(Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_CUSTOM_ORDER_TYPE_ID))
                        json = getCustomOrderContractJson(e.getKey(), e.getValue(), paymentActDate, paymentAnnexDate, project);
                    else if (contractTypeId.equals(Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_CONTRACTOR_TYPE_ID))
                        json = getContractorContractJson(e.getKey(), e.getValue(), paymentActDate, paymentAnnexDate, project);
                    else
                        throw new Exception(String.format("Document with id = %s is not found.", contractTypeId));


                    IssueInputParameters issueInputParameters = issueService.newIssueInputParameters();
                    issueInputParameters.setProjectId(Consts.PAYMENT_ACT_PROJECT_ID);
                    issueInputParameters.setIssueTypeId(String.valueOf(Consts.PAYMENT_ACT_ISSUE_TYPE_ID));
                    issueInputParameters.setReporterId(getLoggedInApplicationUser().getName());
                    issueInputParameters.setAssigneeId(getLoggedInApplicationUser().getName());
                    issueInputParameters.setSummary(String.format(new Locale("ru"), "%s, %s, %s, %,d \u0440\u0443\u0431", PAYMENT_ACT, e.getKey().getFullName(), option.getValue(), (int) e.getValue().totalCost));
                    issueInputParameters.setDescription(PAYMENT_ACT);
                    issueInputParameters.setComponentIds(Consts.PAYMENT_ACT_COMPONENT_VALUE);
                    issueInputParameters.addCustomFieldValue(Consts.PAYMENT_ACT_LEGAL_ENTITY_CF_ID, Consts.PAYMENT_ACT_LEGAL_ENTITY_VALUE);
                    issueInputParameters.addCustomFieldValue(Consts.PAYMENT_ACT_CONTRAGENT_CF_ID, Consts.PAYMENT_ACT_CONTRAGENT_VALUE);
                    issueInputParameters.addCustomFieldValue(Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_CF_ID, json.toString());
                    issueInputParameters.addCustomFieldValue(Consts.PAYMENT_ACT_PROJECT_CF_ID, Consts.PAYMENT_ACT_PROJECT_VALUE_MAP.get(project.getId()));
                    IssueService.CreateValidationResult createValidationResult = issueService.validateCreate(getLoggedInApplicationUser().getDirectoryUser(), issueInputParameters);

                    if (createValidationResult.isValid()) {
                        result.add(new CollectedPreparedIssueData(createValidationResult, e.getKey(), e.getValue()));
                    } else {
                        addErrorMessages(createValidationResult.getErrorCollection().getErrorMessages());
                        addErrorMessages(createValidationResult.getErrorCollection().getErrors().values());
                    }
                }
            }
        }
        return result;
    }

    @RequiresXsrfCheck
    @Override
    public String doExecute() throws Exception {
        if (!isUserAllowed())
            return sendError(HttpServletResponse.SC_UNAUTHORIZED);

        for (Project project : projects)
            if (pluginData.isActCreated(project, option))
                addError("project-" + project.getId(), getText("ru.mail.jira.plugins.contentprojects.extras.createActs.error.alreadyCreated", option.getValue(), project.getName()));
        if (hasAnyErrors())
            return INPUT;

        Collection<CollectedPreparedIssueData> preparedIssues = prepareIssues();
        if (!hasAnyErrors() && preparedIssues.isEmpty())
            addErrorMessage(getText("issuenav.results.none.found"));
        if (hasAnyErrors())
            return INPUT;

        Collection<String> issueKeys = new ArrayList<String>(preparedIssues.size());
        for (CollectedPreparedIssueData preparationResult : preparedIssues) {
            IssueService.IssueResult issueResult = issueService.create(getLoggedInApplicationUser().getDirectoryUser(), preparationResult.createValidationResult);
            if (issueResult.isValid()) {
                IssueLinkService.AddIssueLinkValidationResult addIssueLinkValidationResult = issueLinkService.validateAddIssueLinks(getLoggedInApplicationUser().getDirectoryUser(), issueResult.getIssue(), Consts.PAYMENT_ACT_LINK_TYPE, preparationResult.collectedFreelancerData.issueKeys);
                if (addIssueLinkValidationResult.isValid()) {
                    issueLinkService.addIssueLinks(getLoggedInApplicationUser().getDirectoryUser(), addIssueLinkValidationResult);
                } else {
                    addErrorMessages(addIssueLinkValidationResult.getErrorCollection().getErrorMessages());
                    addErrorMessages(addIssueLinkValidationResult.getErrorCollection().getErrors().values());
                }
                issueKeys.add(issueResult.getIssue().getKey());
            } else {
                addErrorMessages(issueResult.getErrorCollection().getErrorMessages());
                addErrorMessages(issueResult.getErrorCollection().getErrors().values());
            }
        }

        for (Project project : projects)
            pluginData.setActCreated(project, option, true);
        boolean found = false;
        for (Project project : getContentProjects())
            if (!pluginData.isActCreated(project, option)) {
                found = true;
                break;
            }
        if (!found)
            optionsManager.disableOption(option);

        if (hasAnyErrors())
            return INPUT;
        Query query = JqlQueryBuilder.newClauseBuilder().issue(issueKeys.toArray(new String[issueKeys.size()])).buildQuery();
        return forceRedirect("/secure/IssueNavigator.jspa?reset=true" + searchService.getQueryString(getLoggedInApplicationUser().getDirectoryUser(), query));
    }

    @SuppressWarnings("UnusedDeclaration")
    public String getPaymentMonthFieldName() {
        return CommonUtils.getCustomField(Consts.PAYMENT_MONTH_CF_ID).getName();
    }

    @SuppressWarnings("UnusedDeclaration")
    public List<Option> getPaymentMonthOptions() {
        return RemainingBudgetResource.getActivePaymentMonthOptions();
    }

    public Collection<Project> getContentProjects() {
        Collection<Project> result = new ArrayList<Project>(Consts.PROJECT_IDS.size());
        for (Long projectId : Consts.PROJECT_IDS)
            result.add(projectManager.getProjectObj(projectId));
        return result;
    }

    @SuppressWarnings("UnusedDeclaration")
    public long getOptionId() {
        return optionId;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setOptionId(long optionId) {
        this.optionId = optionId;
    }

    @SuppressWarnings("UnusedDeclaration")
    public long[] getProjectIds() {
        return projectIds;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setProjectIds(long[] projectIds) {
        this.projectIds = projectIds;
    }

    @GET
    @Path("/enableOption")
    public Response enableOption(@QueryParam("optionId") final long optionId,
                                 @QueryParam("projectKey") final String projectKey) {
        return new RestExecutor<Void>() {
            @Override
            protected Void doAction() throws Exception {
                Option option = optionsManager.findByOptionId(optionId);
                if (option == null)
                    throw new IllegalArgumentException("Option ID is invalid.");

                Project project = projectManager.getProjectObjByKeyIgnoreCase(projectKey);
                if (project == null || !Consts.PROJECT_IDS.contains(project.getId()))
                    throw new IllegalArgumentException("Project key is invalid.");

                pluginData.setActCreated(project, option, false);
                return null;
            }
        }.getResponse();
    }
}
