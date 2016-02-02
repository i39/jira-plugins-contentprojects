package ru.mail.jira.plugins.contentprojects.issue.functions;

import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.util.json.JSONObject;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.WorkflowException;
import org.apache.commons.lang3.StringUtils;
import ru.mail.jira.plugins.commons.CommonUtils;
import ru.mail.jira.plugins.commons.HttpSender;
import ru.mail.jira.plugins.contentprojects.configuration.Counter;
import ru.mail.jira.plugins.contentprojects.configuration.CounterConfig;
import ru.mail.jira.plugins.contentprojects.configuration.CounterManager;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

public class GoalFunction extends AbstractJiraFunctionProvider {
    private final CounterManager counterManager;
    private final JiraAuthenticationContext jiraAuthenticationContext;

    public GoalFunction(CounterManager counterManager, JiraAuthenticationContext jiraAuthenticationContext){
        this.counterManager = counterManager;
        this.jiraAuthenticationContext = jiraAuthenticationContext;
    }

    private int getGoal(String filter, Date publishingDate, int numberOfDays, int counterId, String counterPassword, String goalFormat, String goalParameter) throws Exception {
        int result = 0;

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(publishingDate);
        for (int day = 0; day < numberOfDays; day++) {
            String date = new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());
            calendar.add(Calendar.DATE, 1);

            String response = new HttpSender("http://top.mail.ru/json/goals?id=%s&password=%s&period=0&date=%s&goal=%s", counterId, counterPassword, date, goalFormat.replace("{filter}", filter)).sendGet();
            JSONObject json = new JSONObject(response);
            if (json.has(goalParameter) && !json.isNull(goalParameter))
                result += json.getLong(goalParameter);
        }

        return result;
    }

    @Override
    public void execute(Map transientVars, Map args, PropertySet ps) throws WorkflowException {
        MutableIssue issue = getIssue(transientVars);

        CustomField publishingDateCf = CommonUtils.getCustomField((String) args.get(AbstractFunctionFactory.PUBLISHING_DATE_FIELD));
        CustomField goalCf = CommonUtils.getCustomField((String) args.get(AbstractFunctionFactory.GOAL_FIELD));
        CustomField urlCf = CommonUtils.getCustomField((String) args.get(AbstractFunctionFactory.URL_FIELD));
        Counter counter = counterManager.getCounter(Integer.parseInt((String) args.get(AbstractFunctionFactory.COUNTER)));
        int numberOfDays = Integer.parseInt((String) args.get(AbstractFunctionFactory.NUMBER_OF_DAYS));
        String goalFormat = (String) args.get(AbstractFunctionFactory.GOAL_FORMAT);
        String goalParameter = (String) args.get(AbstractFunctionFactory.GOAL_PARAMETER);
        boolean ignoreExceptions = Boolean.parseBoolean((String) args.get(AbstractFunctionFactory.IGNORE_EXCEPTIONS));

        CounterConfig counterConfig = counterManager.getCounterConfig(counter, issue.getProjectObject());
        if (counterConfig == null || counterConfig.getRatingId() == null)
            throw new WorkflowException(jiraAuthenticationContext.getI18nHelper().getText("ru.mail.jira.plugins.contentprojects.issue.functions.notConfiguredCounterError", counter.getName()));
        if (counterConfig.getRatingId() == 0)
            return;

        Date publishingDate = (Date) issue.getCustomFieldValue(publishingDateCf);
        String url = (String) issue.getCustomFieldValue(urlCf);
        if (publishingDate == null || StringUtils.isEmpty(url))
            throw new WorkflowException(jiraAuthenticationContext.getI18nHelper().getText("ru.mail.jira.plugins.contentprojects.issue.functions.emptyFieldsError"));

        try {
            int goal = getGoal(AbstractFunctionFactory.getFilter(url), publishingDate, numberOfDays, counterConfig.getRatingId(), StringUtils.trimToEmpty(counterConfig.getRatingPassword()), goalFormat, goalParameter);
            issue.setCustomFieldValue(goalCf, (double) goal);
        } catch (Exception e) {
            if (!ignoreExceptions) {
                AbstractFunctionFactory.sendErrorEmail("ru.mail.jira.plugins.contentprojects.issue.functions.counterError", counter.getName(), issue, Arrays.asList(urlCf, publishingDateCf));
                throw new WorkflowException(jiraAuthenticationContext.getI18nHelper().getText("ru.mail.jira.plugins.contentprojects.issue.functions.counterError", counter.getName()), e);
            }
        }
    }
}
