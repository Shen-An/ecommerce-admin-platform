package com.youlai.dev;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 本地一键主启动类：网关 / 认证 / 系统 / AI / 商品 / 会员 / 订单 / 营销。
 * <p>
 * IDEA：打开本类 → 右键 Run 'PlatformLauncher.main()'
 * 命令行：mvn -pl dev-launcher exec:java
 * <p>
 * 参数：
 * --build          启动前先 mvn package（首次或改过代码时建议）
 * --skip-ai        不启动 mall-ai
 * --skip-pms       不启动 mall-pms
 * --skip-oms       不启动 mall-oms
 * --skip-ums       不启动 mall-ums
 * --skip-sms       不启动 mall-sms
 * --with-oms / --with-pms-oms  兼容旧参数（现默认已启 oms）
 */
public class PlatformLauncher {

    private static final List<Process> CHILDREN = new ArrayList<>();
    private static final Map<String, ServiceDef> SERVICES = new LinkedHashMap<>();

    static {
        SERVICES.put("gateway", new ServiceDef(
                "youlai-gateway",
                "youlai-gateway",
                "youlai-gateway/target/youlai-gateway.jar",
                9999,
                "http://localhost:9999/actuator/health",
                true
        ));
        SERVICES.put("auth", new ServiceDef(
                "youlai-auth",
                "youlai-auth",
                "youlai-auth/target/youlai-auth.jar",
                9000,
                "http://localhost:9000/api/v1/auth/captcha",
                true
        ));
        SERVICES.put("system", new ServiceDef(
                "youlai-system/system-boot",
                "system-boot",
                "youlai-system/system-boot/target/system-boot.jar",
                8800,
                "http://localhost:8800/actuator/health",
                true
        ));
        SERVICES.put("ai", new ServiceDef(
                "mall-ai/ai-boot",
                "ai-boot",
                "mall-ai/ai-boot/target/ai-boot.jar",
                8805,
                "http://localhost:8805/api/v1/ai/health",
                true
        ));
        SERVICES.put("pms", new ServiceDef(
                "mall-pms/pms-boot",
                "pms-boot",
                "mall-pms/pms-boot/target/pms-boot.jar",
                8802,
                "http://localhost:8802/actuator/health",
                true
        ));
        SERVICES.put("ums", new ServiceDef(
                "mall-ums/ums-boot",
                "ums-boot",
                "mall-ums/ums-boot/target/ums-boot.jar",
                8801,
                "http://localhost:8801/actuator/health",
                true
        ));
        SERVICES.put("oms", new ServiceDef(
                "mall-oms/oms-boot",
                "oms-boot",
                "mall-oms/oms-boot/target/oms-boot.jar",
                8803,
                "http://localhost:8803/actuator/health",
                true
        ));
        SERVICES.put("sms", new ServiceDef(
                "mall-sms/sms-boot",
                "sms-boot",
                "mall-sms/sms-boot/target/sms-boot.jar",
                8804,
                "http://localhost:8804/actuator/health",
                true
        ));
    }

    public static void main(String[] args) throws Exception {
        boolean build = hasFlag(args, "--build");
        boolean skipAi = hasFlag(args, "--skip-ai");
        boolean skipPms = hasFlag(args, "--skip-pms");
        boolean skipOms = hasFlag(args, "--skip-oms");
        boolean skipUms = hasFlag(args, "--skip-ums");
        boolean skipSms = hasFlag(args, "--skip-sms");
        // 兼容旧参数
        if (hasFlag(args, "--with-oms") || hasFlag(args, "--with-pms-oms")) {
            skipOms = false;
        }

        Path backendRoot = resolveBackendRoot();
        System.out.println("=================================================");
        System.out.println("  ecommerce-admin-platform 一键启动");
        System.out.println("  backend = " + backendRoot.toAbsolutePath());
        System.out.println("=================================================");

        Runtime.getRuntime().addShutdownHook(new Thread(PlatformLauncher::stopAll, "platform-shutdown"));

        if (build || !allJarsExist(backendRoot, skipAi, skipPms, skipOms, skipUms, skipSms)) {
            System.out.println("[launcher] 打包核心模块 ...");
            runMavenPackage(backendRoot, skipAi, skipPms, skipOms, skipUms, skipSms);
        }

        List<String> order = new ArrayList<>();
        order.add("gateway");
        order.add("auth");
        order.add("system");
        if (!skipAi) {
            order.add("ai");
        }
        if (!skipPms) {
            order.add("pms");
        }
        if (!skipUms) {
            order.add("ums");
        }
        if (!skipOms) {
            order.add("oms");
        }
        if (!skipSms) {
            order.add("sms");
        }

        for (String key : order) {
            ServiceDef svc = SERVICES.get(key);
            if (isPortOpen(svc.port)) {
                System.out.println("[launcher] " + key + " 端口 " + svc.port + " 已占用，跳过启动（假定已在运行）");
                continue;
            }
            startService(backendRoot, key, svc);
            // 给 Nacos 注册一点时间
            Thread.sleep(3000L);
        }

        System.out.println();
        System.out.println("[launcher] 健康检查 ...");
        for (String key : order) {
            ServiceDef svc = SERVICES.get(key);
            boolean ok = waitHealthy(svc, Duration.ofSeconds(90));
            System.out.println("  - " + key + " :" + svc.port + " => " + (ok ? "OK" : "TIMEOUT/FAIL"));
        }

        System.out.println();
        System.out.println("完成。验证码接口：http://localhost:9999/youlai-auth/api/v1/auth/captcha");
        System.out.println("前端：http://localhost:9527  （另开终端 pnpm run dev）");
        System.out.println("按 Ctrl+C 结束本启动器并关闭由其拉起的子进程。");
        System.out.println();

        // 阻塞主线程，保持进程与 shutdown hook
        Thread.currentThread().join();
    }

    private static void startService(Path backendRoot, String key, ServiceDef svc) throws IOException {
        Path jar = backendRoot.resolve(svc.jarRelative);
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("找不到 jar: " + jar + " ，请先 --build 或 Maven package");
        }
        Path logDir = backendRoot.resolve("logs/launcher");
        Files.createDirectories(logDir);
        Path logFile = logDir.resolve(key + ".log");

        String javaBin = resolveJavaBin();
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-Xms256m");
        cmd.add("-Xmx512m");
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dspring.profiles.active=dev");
        cmd.add("-jar");
        cmd.add(jar.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(backendRoot.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

        System.out.println("[launcher] 启动 " + key + " => " + String.join(" ", cmd));
        System.out.println("[launcher] 日志: " + logFile.toAbsolutePath());
        Process p = pb.start();
        CHILDREN.add(p);
    }

    private static void runMavenPackage(Path backendRoot, boolean skipAi, boolean skipPms,
                                        boolean skipOms, boolean skipUms, boolean skipSms) throws Exception {
        List<String> modules = new ArrayList<>();
        modules.add("youlai-gateway");
        modules.add("youlai-auth");
        modules.add("youlai-system/system-boot");
        if (!skipAi) {
            modules.add("mall-ai/ai-boot");
        }
        if (!skipPms) {
            modules.add("mall-pms/pms-boot");
        }
        if (!skipUms) {
            modules.add("mall-ums/ums-boot");
        }
        if (!skipOms) {
            modules.add("mall-oms/oms-boot");
        }
        if (!skipSms) {
            modules.add("mall-sms/sms-boot");
        }
        String pl = String.join(",", modules);
        List<String> cmd = new ArrayList<>();
        cmd.add(resolveMvnCmd());
        cmd.add("-pl");
        cmd.add(pl);
        cmd.add("-am");
        cmd.add("package");
        cmd.add("-DskipTests");
        cmd.add("-q");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(backendRoot.toFile());
        pb.inheritIO();
        Process p = pb.start();
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException("Maven package 失败, exit=" + code);
        }
    }

    private static boolean allJarsExist(Path backendRoot, boolean skipAi, boolean skipPms,
                                        boolean skipOms, boolean skipUms, boolean skipSms) {
        for (String key : List.of("gateway", "auth", "system")) {
            if (!Files.isRegularFile(backendRoot.resolve(SERVICES.get(key).jarRelative))) {
                return false;
            }
        }
        if (!skipAi && !Files.isRegularFile(backendRoot.resolve(SERVICES.get("ai").jarRelative))) {
            return false;
        }
        if (!skipPms && !Files.isRegularFile(backendRoot.resolve(SERVICES.get("pms").jarRelative))) {
            return false;
        }
        if (!skipUms && !Files.isRegularFile(backendRoot.resolve(SERVICES.get("ums").jarRelative))) {
            return false;
        }
        if (!skipOms && !Files.isRegularFile(backendRoot.resolve(SERVICES.get("oms").jarRelative))) {
            return false;
        }
        if (!skipSms && !Files.isRegularFile(backendRoot.resolve(SERVICES.get("sms").jarRelative))) {
            return false;
        }
        return true;
    }

    private static boolean waitHealthy(ServiceDef svc, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (httpOk(svc.healthUrl)) {
                return true;
            }
            TimeUnit.SECONDS.sleep(2);
        }
        return httpAny(svc.healthUrl);
    }

    private static boolean httpOk(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 500;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean httpAny(String url) {
        return httpOk(url);
    }

    private static boolean isPortOpen(int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void stopAll() {
        System.out.println("[launcher] 正在停止子进程 ...");
        for (Process p : CHILDREN) {
            p.destroy();
        }
        for (Process p : CHILDREN) {
            try {
                if (!p.waitFor(5, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Path resolveBackendRoot() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Paths.get("").toAbsolutePath().normalize());
        candidates.add(Paths.get("").toAbsolutePath().normalize().resolve("backend"));
        Path parent = Paths.get("").toAbsolutePath().normalize().getParent();
        if (parent != null) {
            candidates.add(parent);
        }
        try {
            URI loc = PlatformLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path codePath = Paths.get(loc).toAbsolutePath().normalize();
            candidates.add(codePath);
            if (codePath.getParent() != null) {
                candidates.add(codePath.getParent());
            }
        } catch (Exception ignored) {
            // ignore
        }
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            candidates.add(Paths.get(userDir).toAbsolutePath().normalize());
            candidates.add(Paths.get(userDir).toAbsolutePath().normalize().resolve("backend"));
        }

        for (Path start : candidates) {
            Path p = start;
            for (int i = 0; i < 10 && p != null; i++) {
                if (isBackendRoot(p)) {
                    return p;
                }
                p = p.getParent();
            }
        }
        throw new IllegalStateException("无法定位 backend 根目录，请在 backend 下运行 PlatformLauncher");
    }

    private static boolean isBackendRoot(Path p) {
        return Files.isRegularFile(p.resolve("pom.xml"))
                && Files.isDirectory(p.resolve("youlai-gateway"))
                && Files.isDirectory(p.resolve("youlai-auth"));
    }

    private static String resolveJavaBin() {
        String home = System.getProperty("java.home");
        if (home != null) {
            Path bin = Paths.get(home, "bin", isWindows() ? "java.exe" : "java");
            if (Files.isRegularFile(bin)) {
                return bin.toString();
            }
        }
        return "java";
    }

    private static String resolveMvnCmd() {
        if (isWindows()) {
            return "mvn.cmd";
        }
        return "mvn";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String a : args) {
            if (flag.equals(a)) {
                return true;
            }
        }
        return false;
    }

    private record ServiceDef(
            String modulePath,
            String artifact,
            String jarRelative,
            int port,
            String healthUrl,
            boolean core
    ) {
    }
}
