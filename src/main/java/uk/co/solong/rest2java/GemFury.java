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
import org.apache.commons.lang3.Validate;
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
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Mojo(name = "gemfury", defaultPhase = LifecyclePhase.DEPLOY)
public class GemFury extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}/${project.artifactId}.deb")
    private File debFile;

    @Parameter(defaultValue = "https://push.fury.io/secret-token/")
    private URL gemfuryUrl;

    @Parameter
    private String token;

    public Boolean getIgnoreHttpsCertificateWarnings() {
        return ignoreHttpsCertificateWarnings;
    }

    public void setIgnoreHttpsCertificateWarnings(Boolean ignoreHttpsCertificateWarnings) {
        this.ignoreHttpsCertificateWarnings = ignoreHttpsCertificateWarnings;
    }

    @Parameter(defaultValue = "false")
    private Boolean ignoreHttpsCertificateWarnings;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    public void execute() throws MojoExecutionException {
        getLog().info("Publishing debian artifacts to gemfury repository. Token Present: "+((token != null && token.length() > 1)? "Yes":"No"));
        getLog().debug("URL Template "+ gemfuryUrl.toString());
        RetryTemplate t = new RetryTemplate();
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(30000L);
        t.setBackOffPolicy(backOffPolicy);
        SimpleRetryPolicy s = new SimpleRetryPolicy();
        s.setMaxAttempts(99);
        t.setRetryPolicy(s);
        Validate.isTrue(gemfuryUrl.toString().contains("secret-token"), "Malformed gemfury URL, expected the word secret-token where the secret-token should be inserted");
        try {
            final String secretUrl = gemfuryUrl.toString().replace("secret-token", token);

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
                    HttpPost httppost = new HttpPost(secretUrl.toString());
                    httppost.setConfig(config);

                    FileBody packagea = new FileBody(new File(debFile.getPath()));

                    MultipartEntityBuilder buil = MultipartEntityBuilder.create();
                    HttpEntity reqEntity = buil.addPart(FormBodyPartBuilder.create("package", packagea).build()).build();

                    httppost.setEntity(reqEntity);

                    HttpResponse response = httpclient.execute(httppost);
                    HttpEntity entity = response.getEntity();
                    InputStream is = entity.getContent();
                    String result = IOUtils.toString(is, "UTF-8");
                    getLog().info(result);
                    getLog().info("StatusCode:"+response.getStatusLine().getStatusCode());
                    IOUtils.closeQuietly(is);
                    if (response.getStatusLine().getStatusCode() != 200) {
                        getLog().warn("Gemfury was unavailable. Retrying");
                        throw new GemfuryNotAvailable();
                    }
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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
