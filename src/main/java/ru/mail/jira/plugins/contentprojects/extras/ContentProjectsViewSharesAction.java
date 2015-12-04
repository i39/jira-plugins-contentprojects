package ru.mail.jira.plugins.contentprojects.extras;

import com.atlassian.jira.security.xsrf.RequiresXsrfCheck;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import org.apache.commons.lang3.StringUtils;
import ru.mail.jira.plugins.commons.CommonUtils;
import ru.mail.jira.plugins.contentprojects.common.Consts;
import ru.mail.jira.plugins.contentprojects.issue.functions.SharesFunction;
import ru.mail.jira.plugins.contentprojects.issue.functions.SharesOutput;
import webwork.action.ServletActionContext;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ContentProjectsViewSharesAction extends JiraWebActionSupport {
    private String[] urls;
    private SharesOutput[] sharesArray;

    public String getUrls() {
        return urls != null ? StringUtils.join(urls, '\n') : null;
    }

    public void setUrls(String urls) {
        this.urls = fillNotEmptyUrls(urls.trim().split("\\s+"));
    }

    private String[] fillNotEmptyUrls(String[] urls) {
        List<String> urlList = new ArrayList<String>(urls.length);
        for (String url : urls) {
            if (StringUtils.isNotEmpty(url))
                urlList.add(url);
        }
        return !urlList.isEmpty() ? urlList.toArray(new String[urlList.size()]) : null;
    }

    public SharesOutput[] getSharesArray() {
        return sharesArray;
    }

    public SharesOutput getSharesTotal() {
        int[] sharesTotal = new int[5];
        for (SharesOutput shares : this.sharesArray) {
            sharesTotal[0] += shares.getFacebook();
            sharesTotal[1] += shares.getMymail();
            sharesTotal[2] += shares.getOdnoklassniki();
            sharesTotal[3] += shares.getTwitter();
            sharesTotal[4] += shares.getVkontakte();
        }

        return new SharesOutput(sharesTotal);
    }

    private String sendError(int code) throws IOException {
        if (ServletActionContext.getResponse() != null)
            ServletActionContext.getResponse().sendError(code);
        return NONE;
    }

    private boolean isUserAllowed() {
        return CommonUtils.isUserInGroups(getLoggedInApplicationUser(), Consts.ACCOUNTANTS_GROUPS);
    }

    @Override
    public String doDefault() throws Exception {
        if (!isUserAllowed())
            return sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return INPUT;
    }

    @Override
    protected void doValidation() {
        if (urls == null || urls.length == 0)
            addError("urls", getText("issue.field.required", getText("ru.mail.jira.plugins.contentprojects.extras.viewShares.links")));
    }

    @RequiresXsrfCheck
    @Override
    public String doExecute() throws Exception {
        if (!isUserAllowed())
            return sendError(HttpServletResponse.SC_UNAUTHORIZED);

        sharesArray = new SharesOutput[urls.length];

        for (int i = 0; i < sharesArray.length; i++)
            sharesArray[i] = new SharesOutput(urls[i], SharesFunction.getShares(urls[i]));

        return SUCCESS;
    }
}
