package utility;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.httpclient.FiberHttpClientBuilder;
import co.paralleluniverse.strands.SuspendableRunnable;
import manager.ReqTaskType;
import manager.RequestTask;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RequestManager {

    private static CloseableHttpClient client = null;
    private static String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:61.0) Gecko/20100101 Firefox/61.0";

    private static void initClient() {
        try {
            TrustStrategy acceptingTrustStrategy = (certificate, authType) -> true;
            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
            int cores = Runtime.getRuntime().availableProcessors();

            client = FiberHttpClientBuilder.
                    create(cores).
                    setHostnameVerifier(SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER).
                    setSSLContext(sslContext).
                    setMaxConnPerRoute(2048).
                    setMaxConnTotal(2048).build();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Не удалось инициализировать HTTP Client");
        }
    }

    public static List<RequestTask> execute(List<RequestTask> tasks) throws Exception {

        if (client == null)
            initClient();

        HashSet<RequestTask> result = new HashSet<>();

        ArrayList<RequestConfig> allProxy = ProxyManager.getProxy();
        ArrayList<RequestConfig> goodProxy = new ArrayList<>();

        final long startTime = new Date().getTime();
        final int initTaskSize = tasks.size();

        ArrayList<RequestTask> taskMultiply = new ArrayList<>(tasks);

        Integer waveCount = 0;
        Integer parseSpeed = 0;
        Integer wave = 0;

        ArrayList<RequestConfig> proxys;
        while (tasks.size() > 0) {

            if (wave != 0) {
                parseSpeed = ((parseSpeed * waveCount) + (wave - taskMultiply.size())) / ++waveCount;
                System.out.println("=============================================================");
                System.out.println("PARSE SPEED: " + parseSpeed);
                System.out.println("=============================================================");
            }
            wave = taskMultiply.size();

            tasks.clear();
            for (int i = 0; tasks.size() < (allProxy.size() > 2048 ? 2048 : allProxy.size() )
                    && tasks.size() < (taskMultiply.size() * 5); i++) {
                if (i == taskMultiply.size())
                    i = 0;

                tasks.add(taskMultiply.get(i));
            }

            final CountDownLatch cdl = new CountDownLatch(tasks.size());

            proxys = new ArrayList<>(goodProxy);
            goodProxy.clear();
            if (goodProxy.size() < tasks.size())
                proxys.addAll(allProxy);

            for (int i = 0; i < proxys.size() && i < tasks.size(); i++) {
                RequestTask task = tasks.get(i);
                RequestConfig proxy = proxys.get(i);

                new Fiber<Void>((SuspendableRunnable) () -> {
                    HttpEntity entity = null;
                    try {
                        String taskUrl = URLDecoder.decode(task.getUrl(), StandardCharsets.UTF_8.toString())
                                .replaceAll("https", "http");
                        HttpGet request = new HttpGet(taskUrl);
                        request.setConfig(proxy);

                        CloseableHttpResponse response = client.execute(request);
//                        System.out.println(response.getStatusLine());
                        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                            entity = response.getEntity();
                            String body = EntityUtils.toString(entity, "UTF-8");

                            if (body.contains("CB327533540")) {

                                /*if (isPhone) {
                                    Header[] allHeaders = response.getHeaders("Set-Cookie");
                                    System.out.println(body);
                                    for (Header header : allHeaders)
                                        System.out.println(header.getName() + ": " + header.getValue());
                                    System.out.println("---------------------------------------");
                                }*/

                                task.setHtml(body);
                                result.add(task);
                                goodProxy.add(proxy);
                            }
//                            else {
//                                if (isPhone)
//                                    Files.write(Paths.get("fail.txt"), (body + "\n\n\n\n\n\n").getBytes(), StandardOpenOption.APPEND);
//                            }

                        }

//                    if (response.getStatusLine().getStatusCode() == 403)
//                        System.out.println(proxy.getProxy());

                        // end of snippet
                    } catch (IOException ex) {
//                        System.err.println("ERROR: " + ex.getMessage());
                    } finally {
//                        cdl.countDown();
                        if (entity != null) {
                            try {
                                EntityUtils.consume(entity);
                            } catch (IOException ignored) {
                            }
                        }
                    }
                }).start();

            }

            cdl.await(10, TimeUnit.SECONDS);

            if (result.size() == 0)
                throw new Exception("За круг было получено 0 результатов");

            taskMultiply.removeAll(result);

//            if (tasks.get(0).getType() == ReqTaskType.ITEM) {
                System.out.println("RESULT_SIZE: " + result.size());
                int progress = (int) ((result.size() * 1.0 / initTaskSize) * 100);
                System.out.println("SEND PROGRESS: " + progress);
//            }
        }

        if (tasks.size() > 0) {
            System.out.println("=============================================================");
            System.out.println(
                    "Время затраченое на " + (tasks.get(0).getType() == ReqTaskType.ITEM ? "обьявления: " : "категории: ")
                            + (new Date().getTime() - startTime) + " ms");
            System.out.println("=============================================================");
        }
        return new ArrayList<>(result);
    }

    public static void closeClient() throws IOException {
        client.close();
        client = null;
    }
}