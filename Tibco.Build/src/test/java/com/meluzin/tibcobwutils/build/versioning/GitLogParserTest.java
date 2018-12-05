package com.meluzin.tibcobwutils.build.versioning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

public class GitLogParserTest {
	@Test
	public void testGetChanges() {
		
		List<ChangeInfo> changes = new GitLogParser().getChanges(GitLogParserTest.class.getResourceAsStream("MultiLineComment"));
		assertNotNull(changes);
		assertEquals(1, changes.size());
		ChangeInfo changeInfo = changes.get(0);
		assertNotNull(changeInfo);
		assertEquals("Jiří Meluzín <jiri.meluzin@vodafone.com>", changeInfo.getAuthor());
		assertEquals("650169407b275539922825a3c228f8385e365cf9", changeInfo.getRevisionInfo());
		assertEquals("Test multi\n" + 
				"line\n" + 
				"comment", changeInfo.getComment());
		List<FileChangeInfo> changedFiles = changeInfo.getChangedFiles();
		assertNotNull(changedFiles);
		assertEquals(1, changedFiles.size());
		FileChangeInfo fileChangeInfo = changedFiles.get(0);
		assertNotNull(fileChangeInfo);
		assertEquals(ChangeStatus.MOVED, fileChangeInfo.getStatus());
		assertEquals(Paths.get("test2.txt.x"), fileChangeInfo.getLocation());
		assertEquals(Optional.of(Paths.get("test2.txt")), fileChangeInfo.getOriginalLocation());
	}
	@Test
	public void testGetChangesGitLog() {
		
		List<ChangeInfo> changes = new GitLogParser().getChanges(GitLogParserTest.class.getResourceAsStream("git-log"));
		assertNotNull(changes);
		
	}
}
