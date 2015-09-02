package uk.co.solong.rest2java;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.Validate;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.mime.FormBodyPartBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Mojo(name = "gemfury", defaultPhase = LifecyclePhase.DEPLOY)
public class GemFury extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}/${project.artifactId}.deb")
    private File debFile;

    @Parameter
    private URL gemfuryUrl;

    @Parameter(defaultValue = "false")
    private Boolean ignoreHttpsCertificateWarnings;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    public void execute() throws MojoExecutionException {
        getLog().info("Publishing debian artifacts to gemfury repository");

        RetryTemplate t = new RetryTemplate();
        t.setRetryPolicy(new SimpleRetryPolicy());
        try {
            Validate.isTrue(gemfuryUrl.toString().contains("@"), "Malformed gemfury URL, expected @ in url");
            Validate.isTrue(gemfuryUrl.toString().contains("//"), "Malformed gemfury URL, expected https:// in url");
            t.execute(new RetryCallback<Void, Throwable>() {
                @Override
                public Void doWithRetry(RetryContext context) throws Throwable {
                    CloseableHttpClient httpclient = null;
                    if (ignoreHttpsCertificateWarnings) {
                        SSLContextBuilder builder = new SSLContextBuilder();
                        builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
                        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
                        httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
                    } else {
                        httpclient = HttpClients.custom().build();
                    }

                    RequestConfig config = RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).setRedirectsEnabled(true).setAuthenticationEnabled(true)
                            .build();
                    HttpPost httppost = new HttpPost(gemfuryUrl.toString());
                    httppost.setConfig(config);

                    String[] secretSplit = gemfuryUrl.toString().split("@");
                    String[] secretSplitAgaint = secretSplit[0].split("//");
                    String secret = secretSplitAgaint[1];
                    httppost.setHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(secret.getBytes()));
                    FileBody packagea = new FileBody(new File(debFile.getPath()));

                    MultipartEntityBuilder buil = MultipartEntityBuilder.create();
                    HttpEntity reqEntity = buil.addPart(FormBodyPartBuilder.create("package", packagea).build()).build();

                    httppost.setEntity(reqEntity);

                    HttpResponse response = httpclient.execute(httppost);
                    HttpEntity entity = response.getEntity();
                    InputStream is = entity.getContent();
                    String result = IOUtils.toString(is, "UTF-8");
                    getLog().debug(result);
                    IOUtils.closeQuietly(is);
                    getLog().info("Gemfury publication success.");
                    return null;
                }
            });
        } catch (IOException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            getLog().info("Gemfury publication failed.");
            getLog().error(e);
            throw new RuntimeException(e);
        } catch (Throwable e) {
            getLog().info("Gemfury publication failed.");
            getLog().error(e);
            throw new RuntimeException(e);
        }

    }

    public File getDebFile() {
        return debFile;
    }

    public void setDebFile(File debFile) {
        this.debFile = debFile;
    }

    public MavenProject getProject() {
        return project;
    }

    public void setProject(MavenProject project) {
        this.project = project;
    }

    public URL getGemfuryUrl() {
        return gemfuryUrl;
    }

    public void setGemfuryUrl(URL gemfuryUrl) {
        this.gemfuryUrl = gemfuryUrl;
    }

}
