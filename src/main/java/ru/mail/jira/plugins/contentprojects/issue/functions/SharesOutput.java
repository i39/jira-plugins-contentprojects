package ru.mail.jira.plugins.contentprojects.issue.functions;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class SharesOutput {
    @XmlElement
    private String url;
    @XmlElement
    private int total;
    @XmlElement
    private int facebook;
    @XmlElement
    private int mymail;
    @XmlElement
    private int odnoklassniki;
    @XmlElement
    private int twitter;
    @XmlElement
    private int vkontakte;

    private SharesOutput() {
    }

    public SharesOutput(int[] shares) {
        this(null, shares);
    }

    public SharesOutput(String url, int[] shares) {
        this.url = url;
        this.total = shares[0] + shares[1] + shares[2] + shares[3] + shares[4];
        this.facebook = shares[0];
        this.mymail = shares[1];
        this.odnoklassniki = shares[2];
        this.twitter = shares[3];
        this.vkontakte = shares[4];
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getFacebook() {
        return facebook;
    }

    public void setFacebook(int facebook) {
        this.facebook = facebook;
    }

    public int getMymail() {
        return mymail;
    }

    public void setMymail(int mymail) {
        this.mymail = mymail;
    }

    public int getOdnoklassniki() {
        return odnoklassniki;
    }

    public void setOdnoklassniki(int odnoklassniki) {
        this.odnoklassniki = odnoklassniki;
    }

    public int getTwitter() {
        return twitter;
    }

    public void setTwitter(int twitter) {
        this.twitter = twitter;
    }

    public int getVkontakte() {
        return vkontakte;
    }

    public void setVkontakte(int vkontakte) {
        this.vkontakte = vkontakte;
    }
}