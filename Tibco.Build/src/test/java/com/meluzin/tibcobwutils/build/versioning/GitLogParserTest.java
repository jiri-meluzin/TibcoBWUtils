package com.meluzin.tibcobwutils.build.versioning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.hamcrest.CoreMatchers;
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
	public void testGetChanges2() {
		
		List<ChangeInfo> changes = new GitLogParser().getChanges(GitLogParserTest.class.getResourceAsStream("MultiLineComment2"));
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
	@Test
	public void testGetChangesMergeCommit() {
		
		List<ChangeInfo> changes = new GitLogParser().getChanges(GitLogParserTest.class.getResourceAsStream("MergeCommit"));
		
		assertNotNull(changes);
		assertEquals(changes.size(), 1);
		assertNotNull(changes.get(0));
		assertEquals(changes.get(0).getMergeHashes().isPresent(), true);
		assertEquals(changes.get(0).getMergeHashes().get().getA(), "62628158");
		assertEquals(changes.get(0).getMergeHashes().get().getB(), "51c79fc7");
		 
	}
	@Test
	public void testEmptyLineComment() {
		
		List<ChangeInfo> changes = new GitLogParser().getChanges(GitLogParserTest.class.getResourceAsStream("EmptyComment"));
		String comment = changes.get(0).getComment();
		System.out.println(comment);
		assertThat("Six line comment", comment.split("\n").length, CoreMatchers.is(6));
		assertNotNull(changes);
		
	}
	@Test
	public void testForkComment() {
		
		List<ChangeInfo> changes = new GitLogParser().getChanges(GitLogParserTest.class.getResourceAsStream("ForkComment"));
		String comment = changes.get(0).getComment();
		assertThat("Six line comment", comment.split("\n").length, CoreMatchers.is(6));
		assertNotNull(changes);
		
	}
	@Test
	public void testGitLog() {
		
		List<ChangeInfo> changes = new GitLogParser().getChanges(GitLogParserTest.class.getResourceAsStream("gitlog"));
		String comment = changes.get(0).getComment();
		assertThat("Six line comment", comment.split("\n").length, CoreMatchers.is(6));
		assertNotNull(changes);
		
	}
	@Test
	public void testPullRequest() {
		
		List<ChangeInfo> changes = new GitLogParser().getChanges(GitLogParserTest.class.getResourceAsStream("PullRequest"));
		assertNotNull(changes);
		
	}
	@Test
	public void testLast10CommitsRequest() {
		
		List<ChangeInfo> changes = new GitLogParser().getChanges(GitLogParserTest.class.getResourceAsStream("Last10Commits"));
		assertNotNull(changes);
		
	}
}


