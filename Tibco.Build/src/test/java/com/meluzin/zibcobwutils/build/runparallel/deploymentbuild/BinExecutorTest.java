package com.meluzin.zibcobwutils.build.runparallel.deploymentbuild;

import java.io.File;
import java.time.Duration;
import java.util.Optional;

import org.junit.Test;

import com.meluzin.tibcobwutils.build.deploymentbuild.BinExecutor;

public class BinExecutorTest {

	@Test
	public void testExecStringOptionalOfDuration() {
			
		BinExecutor exec = BinExecutor.exec("/tib/app/tibco/designer/5.8/bin/buildlibrary -lib /WSDL_CNH.libbuilder -p /tib/app/jenkins2/workspace/workspace/R181123_klement/source/WSDL_Repository -o /tib/app/jenkins2/workspace/workspace/R181123_klement/./projlib/WSDL_CNH.projlib.tmp.projlib -x -s -a /tib/app/jenkins2/workspace/workspace/R181123_klement/./projlib/WSDL_CNH.designtimelibs", Optional.of(Duration.ofMillis(20000)), new File("/tib/app/tibco/designer/5.8/bin/"));
		System.out.println(exec.getStdError());
		System.out.println(exec.getStdOutput());
	}

}
