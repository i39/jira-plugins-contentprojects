package ru.mail.jira.plugins.contentprojects.extras;

import com.atlassian.jira.security.xsrf.RequiresXsrfCheck;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import org.apache.commons.lang3.StringUtils;
import ru.mail.jira.plugins.contentprojects.issue.functions.SharesFunction;
import ru.mail.jira.plugins.contentprojects.issue.functions.SharesOutput;

import java.util.ArrayList;
import java.util.List;

public class ContentProjectsViewSharesAction extends JiraWebActionSupport {
    private String[] urls;
    private SharesOutput[] sharesArray;

    @Override
    public String doDefault() throws Exception {
        return INPUT;
    }

    @Override
    protected void doValidation() {
        if (urls == null || urls.length == 0)
            addError("urls", getText("issue.field.required", getText("ru.mail.jira.plugins.contentprojects.extras.viewShares.urls")));
    }

    @RequiresXsrfCheck
    @Override
    public String doExecute() throws Exception {
        sharesArray = new SharesOutput[urls.length];
        for (int i = 0; i < sharesArray.length; i++)
            sharesArray[i] = new SharesOutput(urls[i], SharesFunction.getShares(urls[i]));
        return SUCCESS;
    }

    @SuppressWarnings("unused")
    public String getUrls() {
        return urls != null ? StringUtils.join(urls, '\n') : null;
    }

    @SuppressWarnings("unused")
    public void setUrls(String urlParam) {
        List<String> urlList = new ArrayList<String>();
        if (StringUtils.isNotBlank(urlParam))
            for (String url : urlParam.trim().split("\\s+"))
                if (StringUtils.isNotEmpty(url))
                    urlList.add(url);
        this.urls = !urlList.isEmpty() ? urlList.toArray(new String[urlList.size()]) : null;
    }

    @SuppressWarnings("unused")
    public SharesOutput[] getSharesArray() {
        return sharesArray;
    }

    @SuppressWarnings("unused")
    public SharesOutput getSharesTotal() {
        int[] sharesTotal = new int[5];
        for (SharesOutput shares : sharesArray) {
            sharesTotal[0] += shares.getFacebook();
            sharesTotal[1] += shares.getMymail();
            sharesTotal[2] += shares.getOdnoklassniki();
            sharesTotal[3] += shares.getTwitter();
            sharesTotal[4] += shares.getVkontakte();
        }
        return new SharesOutput(null, sharesTotal[0], sharesTotal[1], sharesTotal[2], sharesTotal[3], sharesTotal[4]);
    }
}

