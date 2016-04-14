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
    private int facebookWithLikes;
    @XmlElement
    private int mymail;
    @XmlElement
    private int odnoklassniki;
    @XmlElement
    private int twitter;
    @XmlElement
    private int vkontakte;

    /**
     * @deprecated Because it's difficult to control parameters in array,
     * use <code>new SharesOutput(url, facebook, mymail, ok, twitter, vkontakte)</code>.
     */
    @Deprecated
    public SharesOutput(String url, int[] shares) {
        this(url, shares[0], shares[1], shares[2], shares[3], shares[4], shares[5]);
    }

    public SharesOutput(String url, int facebook, int facebookWithLikes, int mymail, int ok, int twitter, int vkontakte) {
        this.url = url;
        this.total = facebook + mymail + ok + twitter + vkontakte;
        this.facebook = facebook;
        this.facebookWithLikes = facebookWithLikes;
        this.mymail = mymail;
        this.odnoklassniki = ok;
        this.twitter = twitter;
        this.vkontakte = vkontakte;
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

    public int getFacebookWithLikes() {
        return facebookWithLikes;
    }

    public void setFacebookWithLikes(int facebookWithLikes) {
        this.facebookWithLikes = facebookWithLikes;
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
