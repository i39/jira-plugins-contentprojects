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
import org.apache.commons.lang3.tuple.Pair;
import ru.mail.jira.plugins.commons.CommonUtils;
import ru.mail.jira.plugins.commons.LocalUtils;
import ru.mail.jira.plugins.commons.RestExecutor;
import ru.mail.jira.plugins.contentprojects.authors.Author;
import ru.mail.jira.plugins.contentprojects.authors.FreelancerAuthor;
import ru.mail.jira.plugins.contentprojects.authors.freelancers.Freelancer;
import ru.mail.jira.plugins.contentprojects.authors.freelancers.FreelancerManager;
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
    private final DateFormat OPTION_FORMAT = LocalUtils.updateMonthNames(new SimpleDateFormat("MMMM yyyy"), LocalUtils.MONTH_NAMES_NOMINATIVE);
    private final DateFormat DAY_OF_THE_MONTH_FORMAT = new SimpleDateFormat("dd");
    private final DateFormat MONTH_FORMAT = LocalUtils.updateMonthNames(new SimpleDateFormat("MMMM"), LocalUtils.MONTH_NAMES_GENITIVE);
    private final DateFormat YEAR_FORMAT = new SimpleDateFormat("yy");
    private final DateFormat DATE_FORMAT = LocalUtils.updateMonthNames(new SimpleDateFormat("dd MMMM yyyy"), LocalUtils.MONTH_NAMES_GENITIVE);

    private final FreelancerManager freelancerManager;
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

    public ContentProjectsCreateActsAction(FreelancerManager freelancerManager, IssueLinkService issueLinkService, IssueService issueService, OptionsManager optionsManager, PluginData pluginData, ProjectManager projectManager, SearchProvider searchProvider, SearchService searchService) {
        this.freelancerManager = freelancerManager;
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

    private Collection<Date> getPossibleActDates() throws ParseException {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(OPTION_FORMAT.parse(option.getValue()));

        Collection<Date> result = new ArrayList<Date>();
        for (int day = 15; day <= 31; day++) {
            calendar.set(Calendar.DAY_OF_MONTH, day);
            if (calendar.get(Calendar.DAY_OF_MONTH) != day)
                break;
            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
                continue;
            if (calendar.get(Calendar.MONTH) == Calendar.FEBRUARY && calendar.get(Calendar.DAY_OF_MONTH) == 23)
                continue;
            result.add(calendar.getTime());
        }
        return result;
    }

    private Collection<Date> getPossibleAnnexDates() throws ParseException {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(OPTION_FORMAT.parse(option.getValue()));

        Collection<Date> result = new ArrayList<Date>();
        for (int day = 1; day <= 15; day++) {
            calendar.set(Calendar.DAY_OF_MONTH, day);

            int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
            if (dayOfMonth != day)
                break;

            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
                continue;

            int month = calendar.get(Calendar.MONTH);
            if (month == Calendar.JANUARY && dayOfMonth < 9)
                continue;
            if (month == Calendar.MARCH && dayOfMonth == 8)
                continue;
            if (month == Calendar.MAY && (dayOfMonth == 1 || dayOfMonth == 9))
                continue;
            if (month == Calendar.JUNE && dayOfMonth == 12)
                continue;
            if (month == Calendar.NOVEMBER && dayOfMonth == 4)
                continue;
            result.add(calendar.getTime());
        }
        return result;
    }

    class FreelancerData {
        Collection<String> issueKeys = new ArrayList<String>();
        Collection<String> issueSummaries = new ArrayList<String>();
        Collection<String> issueDescriptions = new ArrayList<String>();
        Collection<String> worksName = new HashSet<String>();
        double totalCost = 0;
        double totalImages = 0;
    }

    private JSONObject getArticleContractJson(Freelancer freelancer, FreelancerData freelancerData, Date paymentActDate, Project project) throws Exception {
        JSONObject json = new JSONObject();
        json.put("templateIds", Collections.<Object>singleton(Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_ARTICLE_TEMPLATE_ID));
        json.put("variableValues", Arrays.<Object>asList(
                DAY_OF_THE_MONTH_FORMAT.format(freelancer.getContractDate()),
                MONTH_FORMAT.format(freelancer.getContractDate()),
                YEAR_FORMAT.format(freelancer.getContractDate()),
                DAY_OF_THE_MONTH_FORMAT.format(paymentActDate.getTime()),
                MONTH_FORMAT.format(paymentActDate.getTime()),
                YEAR_FORMAT.format(paymentActDate.getTime()),
                freelancerData.issueSummaries.size(),
                StringUtils.join(freelancerData.issueSummaries, "\n"),
                String.format(new Locale("ru"), "%,d", (int) freelancerData.totalCost),
                LocalUtils.numberToCaption((int) freelancerData.totalCost),
                String.format(new Locale("ru"), "%02d", (int) (freelancerData.totalCost * 100 % 100)),
                StringUtils.isNotEmpty(freelancer.getPayeeName()) ? freelancer.getPayeeName() : freelancer.getFullName(),
                project.getName().substring(4)
        ));
        return json;
    }

    private JSONObject getImagesContractJson(Freelancer freelancer, FreelancerData freelancerData, Date paymentActDate, Project project) throws Exception {
        JSONObject json = new JSONObject();
        json.put("templateIds", Collections.<Object>singleton(Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_IMAGE_TEMPLATE_ID));
        json.put("variableValues", Arrays.<Object>asList(
                DAY_OF_THE_MONTH_FORMAT.format(freelancer.getContractDate()),
                MONTH_FORMAT.format(freelancer.getContractDate()),
                YEAR_FORMAT.format(freelancer.getContractDate()),
                DAY_OF_THE_MONTH_FORMAT.format(paymentActDate.getTime()),
                MONTH_FORMAT.format(paymentActDate.getTime()),
                YEAR_FORMAT.format(paymentActDate.getTime()),
                String.format(new Locale("ru"), "%,d", (int) freelancerData.totalImages),
                StringUtils.join(freelancerData.issueDescriptions, "\n"),
                String.format(new Locale("ru"), "%,d", (int) freelancerData.totalCost),
                LocalUtils.numberToCaption((int) freelancerData.totalCost),
                String.format(new Locale("ru"), "%02d", (int) (freelancerData.totalCost * 100 % 100)),
                StringUtils.isNotEmpty(freelancer.getPayeeName()) ? freelancer.getPayeeName() : freelancer.getFullName(),
                project.getName().substring(4)
        ));
        return json;
    }

    private JSONObject getCustomOrderContractJson(Freelancer freelancer, FreelancerData freelancerData, Date paymentActDate, Date paymentAnnexDate, Project project) throws Exception {
        JSONObject json = new JSONObject();
        json.put("templateIds", Collections.<Object>singleton(Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_CUSTOM_ORDER_TEMPLATE_ID));
        json.put("variableValues", Arrays.<Object>asList(
                String.valueOf(freelancer.getLastAnnexNumber() + 1),
                DAY_OF_THE_MONTH_FORMAT.format(freelancer.getContractDate()),
                MONTH_FORMAT.format(freelancer.getContractDate()),
                YEAR_FORMAT.format(freelancer.getContractDate()),
                DATE_FORMAT.format(paymentAnnexDate.getTime()),
                StringUtils.isNotEmpty(freelancer.getPayeeName()) ? freelancer.getPayeeName() : freelancer.getFullName(),
                project.getName().substring(4),
                StringUtils.join(freelancerData.worksName, "\n"),
                String.format(new Locale("ru"), "%,d", (int) freelancerData.totalCost),
                DATE_FORMAT.format(paymentActDate.getTime())
        ));
        return json;
    }

    private JSONObject getContractorContractJson(Freelancer freelancer, FreelancerData freelancerData, Date paymentActDate, Date paymentAnnexDate, Project project) throws Exception {
        JSONObject json = new JSONObject();
        json.put("templateIds", Collections.<Object>singleton(Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_CONTRACTOR_TEMPLATE_ID));
        json.put("variableValues", Arrays.<Object>asList(
                String.valueOf(freelancer.getLastAnnexNumber() + 1),
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
                StringUtils.join(freelancerData.worksName, "\n"),
                String.format(new Locale("ru"), "%,d", (int) freelancerData.totalCost),
                project.getName().substring(4)
        ));
        return json;
    }

    private Collection<Pair<IssueService.CreateValidationResult, Collection<String>>> prepareIssues() throws Exception {
        CustomField paymentMonthCf = CommonUtils.getCustomField(Consts.PAYMENT_MONTH_CF_ID);
        CustomField costCf = CommonUtils.getCustomField(Consts.COST_CF_ID);
        CustomField numberImagesCf = CommonUtils.getCustomField(Consts.IMAGES_NUMBER_CF_ID);
        CustomField textAuthorCf = CommonUtils.getCustomField(Consts.TEXT_AUTHOR_CF_ID);

        Collection<Pair<IssueService.CreateValidationResult, Collection<String>>> result = new ArrayList<Pair<IssueService.CreateValidationResult, Collection<String>>>();
        for (Map.Entry<String, String> contract : Consts.PAYMENT_ACT_TYPICAL_CONTRACTS_TYPES.entrySet()) {
            Collection<Date> possibleActDates = getPossibleActDates();
            Iterator<Date> possibleActDatesIterator = possibleActDates.iterator();
            Collection<Date> possibleAnnexDates = getPossibleAnnexDates();
            Iterator<Date> possibleAnnexDatesIterator = possibleAnnexDates.iterator();

            for (Project project : projects) {
                Query query = JqlQueryBuilder.newClauseBuilder().project(project.getId()).and().issueType(contract.getValue()).buildQuery();
                SearchResults searchResults = searchProvider.search(query, getLoggedInApplicationUser(), PagerFilter.getUnlimitedFilter());

                Map<Freelancer, FreelancerData> freelancerDataMap = new HashMap<Freelancer, FreelancerData>();
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
                    FreelancerData freelancerData = freelancerDataMap.get(freelancer);
                    if (freelancerData == null) {
                        freelancerData = new FreelancerData();
                        freelancerDataMap.put(freelancer, freelancerData);
                    }
                    freelancerData.issueKeys.add(issue.getKey());
                    freelancerData.issueSummaries.add(issue.getSummary());
                    if (issue.getDescription() != null)
                        freelancerData.issueDescriptions.add(issue.getDescription());
                    if (freelancer.getWorksName() != null)
                        freelancerData.worksName.add(freelancer.getWorksName());
                    freelancerData.totalCost += (Double) issue.getCustomFieldValue(costCf);
                    if (issue.getCustomFieldValue(numberImagesCf) != null)
                        freelancerData.totalImages += (Double) issue.getCustomFieldValue(numberImagesCf);
                }

                for (Map.Entry<Freelancer, FreelancerData> e : freelancerDataMap.entrySet()) {
                    Date paymentActDate = possibleActDatesIterator.next();
                    if (!possibleActDatesIterator.hasNext())
                        possibleActDatesIterator = possibleActDates.iterator();

                    Date paymentAnnexDate = possibleAnnexDatesIterator.next();
                    if (!possibleAnnexDatesIterator.hasNext())
                        possibleAnnexDatesIterator = possibleAnnexDates.iterator();

                    JSONObject json = new JSONObject();
                    String contractType = contract.getKey();
                    if (contractType.equals("ARTICLE"))
                        json = getArticleContractJson(e.getKey(), e.getValue(), paymentActDate, project);
                    else if (contractType.equals("IMAGE"))
                        json = getImagesContractJson(e.getKey(), e.getValue(), paymentActDate, project);
                    else if (contractType.equals("CUSTOM_ORDER")) {
                        json = getCustomOrderContractJson(e.getKey(), e.getValue(), paymentActDate, paymentAnnexDate, project);
                        freelancerManager.incrementAnnexNumber(e.getKey().getID());
                    }
                    else if (contractType.equals("CONTRACTOR")) {
                        json = getContractorContractJson(e.getKey(), e.getValue(), paymentActDate, paymentAnnexDate, project);
                        freelancerManager.incrementAnnexNumber(e.getKey().getID());
                    }

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
                        result.add(Pair.of(createValidationResult, e.getValue().issueKeys));
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

        Collection<Pair<IssueService.CreateValidationResult, Collection<String>>> preparedIssues = prepareIssues();
        if (!hasAnyErrors() && preparedIssues.isEmpty())
            addErrorMessage(getText("issuenav.results.none.found"));
        if (hasAnyErrors())
            return INPUT;

        Collection<String> issueKeys = new ArrayList<String>(preparedIssues.size());
        for (Pair<IssueService.CreateValidationResult, Collection<String>> preparationResult : preparedIssues) {
            IssueService.IssueResult issueResult = issueService.create(getLoggedInApplicationUser().getDirectoryUser(), preparationResult.getLeft());
            if (issueResult.isValid()) {
                IssueLinkService.AddIssueLinkValidationResult addIssueLinkValidationResult = issueLinkService.validateAddIssueLinks(getLoggedInApplicationUser().getDirectoryUser(), issueResult.getIssue(), Consts.PAYMENT_ACT_LINK_TYPE, preparationResult.getRight());
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
