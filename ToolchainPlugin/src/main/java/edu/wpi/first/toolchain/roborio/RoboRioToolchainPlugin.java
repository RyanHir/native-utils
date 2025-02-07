package edu.wpi.first.toolchain.roborio;

import edu.wpi.first.toolchain.*;
import edu.wpi.first.toolchain.configurable.CrossCompilerConfiguration;
import edu.wpi.first.toolchain.configurable.DefaultCrossCompilerConfiguration;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.internal.os.OperatingSystem;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public class RoboRioToolchainPlugin implements Plugin<Project> {

    public static final String toolchainName = "roboRio";

    private RoboRioToolchainExtension roborioExt;
    private Project project;

    @Override
    public void apply(Project project) {
        this.project = project;

        roborioExt = project.getExtensions().create("frcToolchain", RoboRioToolchainExtension.class);

        ToolchainExtension toolchainExt = project.getExtensions().getByType(ToolchainExtension.class);

        Property<Boolean> optional = project.getObjects().property(Boolean.class);
        optional.set(true);

        ToolchainDescriptor<RoboRioGcc> descriptor = new ToolchainDescriptor<>(
            project,
            toolchainName,
            "roborioGcc",
            new ToolchainRegistrar<RoboRioGcc>(RoboRioGcc.class, project),
            optional);
        descriptor.setToolchainPlatforms(NativePlatforms.roborio);
        descriptor.getDiscoverers().all((ToolchainDiscoverer disc) -> {
            disc.configureVersions(roborioExt.versionLow, roborioExt.versionHigh);
        });

        CrossCompilerConfiguration configuration = new DefaultCrossCompilerConfiguration(NativePlatforms.roborio, descriptor, optional);
        configuration.setArchitecture("arm");
        configuration.setOperatingSystem("linux");
        configuration.setCompilerPrefix("");

        toolchainExt.getCrossCompilers().add(configuration);

        project.afterEvaluate((Project proj) -> {
            populateDescriptor(descriptor);
        });
    }

    public static File toolchainInstallLoc(String year) {
        return new File(ToolchainPlugin.pluginHome(), "frc/" + year + "/roborio");
    }

    public String composeTool(String toolName) {
        String exeSuffix = OperatingSystem.current().isWindows() ? ".exe" : "";
        return "arm-frc" + roborioExt.year + "-linux-gnueabi-" + toolName + exeSuffix;
    }


    public void populateDescriptor(ToolchainDescriptor<RoboRioGcc> descriptor) {
        File frcHomeLoc = new File(new FrcHome(roborioExt.year).get(), "roborio");
        File installLoc = toolchainInstallLoc(roborioExt.year);

        descriptor.getDiscoverers().add(ToolchainDiscoverer.create("FRCHome", frcHomeLoc, this::composeTool, project));
        descriptor.getDiscoverers().add(ToolchainDiscoverer.create("GradleUserDir", installLoc, this::composeTool, project));
        descriptor.getDiscoverers().addAll(ToolchainDiscoverer.forSystemPath(project, this::composeTool));

        String installerSubdir = "frc" + roborioExt.year + "/roborio";
        try {
            descriptor.getInstallers().add(installerFor(OperatingSystem.LINUX, installLoc, installerSubdir));
            descriptor.getInstallers().add(installerFor(OperatingSystem.WINDOWS, installLoc, installerSubdir));
            descriptor.getInstallers().add(installerFor(OperatingSystem.MAC_OS, installLoc, installerSubdir));
        } catch (MalformedURLException e) {
            throw new GradleException("Malformed Toolchain URL", e);
        }
    }

    private AbstractToolchainInstaller installerFor(OperatingSystem os, File installDir, String subdir) throws MalformedURLException {
        URL url = toolchainDownloadUrl(toolchainRemoteFile());
        return new DefaultToolchainInstaller(os, url, installDir, subdir);
    }

    private String toolchainRemoteFile() {
        String[] desiredVersion = roborioExt.toolchainVersion.split("-");

        String platformId;
        if (OperatingSystem.current().isWindows()) {
            platformId = "Windows" + (NativePlatforms.desktopPlatformArch() == "x86-64" ? "64" : "32");
        } else if (OperatingSystem.current().isMacOsX()) {
            platformId = "Mac";
        } else {
            platformId = "Linux";
        }
        String ext = OperatingSystem.current().isWindows() ? "zip" : "tar.gz";
        return "FRC-" + desiredVersion[0] + "-" + platformId + "-Toolchain-" + desiredVersion[1] + "." + ext;
    }

    private URL toolchainDownloadUrl(String file) throws MalformedURLException {
        return new URL("https://github.com/wpilibsuite/roborio-toolchain/releases/download/" + roborioExt.toolchainTag + "/" + file);
    }

}
