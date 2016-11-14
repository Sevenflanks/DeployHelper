package tw.com.softleader.dh.strategy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import tw.com.softleader.dh.basic.Config;
import tw.com.softleader.dh.basic.TomcatComponent;
import tw.com.softleader.dh.basic.VerifyException;
import tw.com.softleader.dh.basic.ZipUtils;

public class DeployHandler {

	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	// 使用者輸入
	private File deployFile;
	private File tomcatDir;

	// 自動產生
	private Path tomcatBinPath;
	private Path tomcatWebAppPath;
	private File backupFile;
	private File deploiedFile;

	// 全域設定檔
	private Config config;

	public DeployHandler(final Config config) {
		this.config = config;
		if (this.config != null) {
			if (this.config.getTomcatPath() != null && !this.config.getTomcatPath().isEmpty()) {
				tomcatDir = new File(this.config.getTomcatPath());
			} else {
				tomcatDir = Optional.ofNullable(System.getenv("TOMCAT_HOME")).map(File::new).orElse(null);
			}
		}
	}

	public void deploy(final Consumer<String> logHandle, final Consumer<Throwable> errorHandle, final Runnable callback) throws Exception {
		logHandle.accept("資料校驗中...");
		verify();

		CompletableFuture.runAsync(() -> {
			try {
				logHandle.accept("正在嘗試關閉Tomcat...");
				TomcatComponent.shutdownTomcat(config, tomcatBinPath);

				logHandle.accept("正在進行備份...");
				backupWebApps();

				logHandle.accept("佈署中...");
				copyWarToWebApp();
				doBeforeStart();

				TomcatComponent.startupTomcat(config, tomcatBinPath);
				logHandle.accept("佈署完畢，您已經可以結束此佈署程式");
			} catch (final Exception e) {
				logHandle.accept("異常發生，中斷操作");
				errorHandle.accept(e);
			} finally {
				callback.run();
			}
		});

	}

	private void verify() throws VerifyException {
		final List<String> msgs = new ArrayList<>();

		if (!isFileCanUse(deployFile)) {
			msgs.add("請選擇佈署檔");
		}

		if (!isFileCanUse(tomcatDir)) {
			msgs.add("請選擇佈署路徑");
		} else {
			final Path tomcatPath = tomcatDir.toPath();

			this.tomcatBinPath = tomcatPath.resolve("bin");
			if (!isPathCanUse(this.tomcatBinPath)) {
				msgs.add("找不到 " + this.tomcatBinPath.toString() + " 請確認您選擇的是正確的Tomcat");
			}

			this.tomcatWebAppPath = tomcatPath.resolve("webapps");
			if (!isPathCanUse(this.tomcatWebAppPath)) {
				msgs.add("找不到 " + this.tomcatWebAppPath.toString() + " 請確認您選擇的是正確的Tomcat");
			}
		}

		if (!msgs.isEmpty()) {
			throw new VerifyException(msgs);
		} else {
			config.verify();
		}
	}

	private void backupWebApps() throws Exception {
		backupFile = new File(tomcatDir.getPath() + "\\webapps." + LocalDateTime.now().format(formatter) + ".zip");
		try (final ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(backupFile)) {
			ZipUtils.compress(zaos, tomcatWebAppPath.toFile());
		} catch (final Exception e) {
			throw e;
		}
	}

	private void copyWarToWebApp() throws Exception {
		deploiedFile = tomcatWebAppPath.resolve(deployFile.getName()).toFile();
		try (
			final FileInputStream input = new FileInputStream(deployFile);
			final FileOutputStream output = new FileOutputStream(deploiedFile);
		) {
			IOUtils.copy(input, output);
		} catch (final Exception e) {
			throw e;
		}
	}

	private void doBeforeStart() {
		if (!config.isKeepBackUpFile()) {
			backupFile.delete();
		}
	}

	private boolean isPathCanUse(final Path path) {
		return path == null || Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
	}

	private boolean isFileCanUse(final File file) {
		return file != null && file.exists();
	}

	public File getDeployFile() {
		return deployFile;
	}

	public File getTomcatDir() {
		return tomcatDir;
	}

	public Path getTomcatBinPath() {
		return tomcatBinPath;
	}

	public Path getTomcatWebAppPath() {
		return tomcatWebAppPath;
	}

	public File getBackupFile() {
		return backupFile;
	}

	public File getDeploiedFile() {
		return deploiedFile;
	}

	public void setDeployFile(final File deployFile) {
		this.deployFile = deployFile;
	}

	public void setTomcatDir(final File deployTomcatPath) {
		config.setTomcatPath(deployTomcatPath.getPath());
		this.tomcatDir = deployTomcatPath;
	}

}