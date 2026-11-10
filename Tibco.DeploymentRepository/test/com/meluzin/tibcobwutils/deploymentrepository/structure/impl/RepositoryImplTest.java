package com.meluzin.tibcobwutils.deploymentrepository.structure.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.meluzin.tibcobwutils.deploymentrepository.structure.Alias;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Config;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Item;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Repository;

public class RepositoryImplTest {
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();
	private Path root;

	private static final Config EMPTY_CONFIG = new Config() {
		@Override
		public Collection<Alias> getAliases() {
			return Collections.emptyList();
		}
		@Override
		public Alias getAliasByName(String name) {
			return null;
		}
		@Override
		public Path getConfigPath() {
			return null;
		}
	};

	@Before
	public void setUp() throws IOException {
		root = tmp.getRoot().toPath();
		Files.createDirectories(root.resolve("defaultVars"));
		Files.createDirectories(root.resolve("Processes"));
		Files.write(root.resolve("Processes").resolve("a.txt"), "A".getBytes(StandardCharsets.UTF_8));
		Files.write(root.resolve("Processes").resolve("b.txt"), "B".getBytes(StandardCharsets.UTF_8));
	}

	private Repository open() {
		return new RepositoryImpl(root, EMPTY_CONFIG);
	}

	@Test
	public void findItemReturnsExistingFile() {
		Repository repository = open();
		assertTrue(repository.findItem("Processes/a.txt").isPresent());
		assertFalse(repository.findItem("Processes/missing.txt").isPresent());
	}

	@Test
	public void createItemIsVisibleButNotOnDiskUntilSave() throws IOException {
		Repository repository = open();
		Item item = repository.createItem("Processes/new.txt");
		try (OutputStream out = item.setContent()) {
			out.write("N".getBytes(StandardCharsets.UTF_8));
		}

		assertTrue(repository.findItem("Processes/new.txt").isPresent());
		assertTrue(repository.isChanged());
		assertFalse(Files.exists(root.resolve("Processes").resolve("new.txt")));

		repository.save();

		assertEquals("N", new String(Files.readAllBytes(root.resolve("Processes").resolve("new.txt")), StandardCharsets.UTF_8));
	}

	@Test
	public void removeItemHidesItImmediatelyButKeepsFileUntilSave() {
		Repository repository = open();
		Item item = repository.findItem("Processes/a.txt").get();

		repository.removeItem(item);

		assertFalse(repository.findItem("Processes/a.txt").isPresent());
		assertTrue(repository.findItem("Processes/b.txt").isPresent());
		assertTrue(repository.isChanged());
		assertTrue(Files.exists(root.resolve("Processes").resolve("a.txt")));
		assertEquals(1, repository.getDeployment().getRemovedItems().size());
	}

	@Test
	public void saveDeletesRemovedItemFromDisk() {
		Repository repository = open();
		repository.removeItem(repository.findItem("Processes/a.txt").get());

		repository.save();

		assertFalse(Files.exists(root.resolve("Processes").resolve("a.txt")));
		assertTrue(Files.exists(root.resolve("Processes").resolve("b.txt")));
		assertFalse(open().findItem("Processes/a.txt").isPresent());
	}

	@Test
	public void removingFolderRemovesWholeSubtreeFromDiskAfterSave() throws IOException {
		Path folder = root.resolve("Folder");
		Files.createDirectories(folder.resolve("Sub1").resolve("Deep"));
		Files.createDirectories(folder.resolve("Sub2"));
		write(folder.resolve("top.txt"), "T");
		write(folder.resolve("Sub1").resolve("x.txt"), "X");
		write(folder.resolve("Sub1").resolve("Deep").resolve("y.txt"), "Y");
		write(folder.resolve("Sub2").resolve("z.txt"), "Z");

		Repository repository = open();
		Item sub1 = repository.findItem("Folder/Sub1").get();
		assertTrue(sub1.isFolder());

		repository.removeItem(sub1);
		repository.save();

		assertFalse(Files.exists(folder.resolve("Sub1")));
		assertTrue(Files.exists(folder.resolve("Sub2").resolve("z.txt")));
		assertTrue(Files.exists(folder.resolve("top.txt")));

		Repository reloaded = open();
		assertFalse(reloaded.findItem("Folder/Sub1").isPresent());
		assertFalse(reloaded.findItem("Folder/Sub1/Deep/y.txt").isPresent());
		assertTrue(reloaded.findItem("Folder/Sub2/z.txt").isPresent());
	}

	private static void write(Path path, String text) throws IOException {
		Files.write(path, text.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	public void addThenRemoveBeforeSaveLeavesNothingOnDisk() {
		Repository repository = open();
		Item created = repository.createItem("Processes/temp.txt");

		repository.removeItem(created);
		repository.save();

		assertFalse(repository.findItem("Processes/temp.txt").isPresent());
		assertFalse(Files.exists(root.resolve("Processes").resolve("temp.txt")));
		assertFalse(open().findItem("Processes/temp.txt").isPresent());
	}

	@Test
	public void removeThenAddSameNameBeforeSaveKeepsNewContent() throws IOException {
		Repository repository = open();
		repository.removeItem(repository.findItem("Processes/a.txt").get());

		Item recreated = repository.createItem("Processes/a.txt");
		try (OutputStream out = recreated.setContent()) {
			out.write("NEW".getBytes(StandardCharsets.UTF_8));
		}
		repository.save();

		assertEquals("NEW", new String(Files.readAllBytes(root.resolve("Processes").resolve("a.txt")), StandardCharsets.UTF_8));
		assertEquals(1, countChildren(open(), "Processes", "a.txt"));
	}

	@Test
	public void creatingSameItemTwiceReturnsSameItem() {
		Repository repository = open();

		Item first = repository.createItem("Processes/dup.txt");
		Item second = repository.createItem("Processes/dup.txt");
		repository.save();

		assertEquals(first.getPath(), second.getPath());
		assertEquals(1, countChildren(open(), "Processes", "dup.txt"));
	}

	@Test
	public void removingSameItemTwiceIsHarmless() {
		Repository repository = open();
		Item a = repository.findItem("Processes/a.txt").get();

		repository.removeItem(a);
		repository.removeItem(a);
		repository.save();

		assertFalse(Files.exists(root.resolve("Processes").resolve("a.txt")));
		assertTrue(Files.exists(root.resolve("Processes").resolve("b.txt")));
	}

	@Test
	public void lastContentWinsWhenUpdatedSeveralTimesBeforeSave() throws IOException {
		Repository repository = open();
		Item a = repository.findItem("Processes/a.txt").get();

		for (String value : new String[] { "1", "2", "3" }) {
			try (OutputStream out = a.setContent()) {
				out.write(value.getBytes(StandardCharsets.UTF_8));
			}
		}
		repository.save();

		assertEquals("3", new String(Files.readAllBytes(root.resolve("Processes").resolve("a.txt")), StandardCharsets.UTF_8));
	}

	@Test
	public void mixedAddAndRemoveInDifferentFoldersAreAllAppliedOnSave() throws IOException {
		Files.createDirectories(root.resolve("Other"));
		write(root.resolve("Other").resolve("o.txt"), "O");
		Repository repository = open();

		repository.createItem("Processes/added1.txt");
		repository.removeItem(repository.findItem("Other/o.txt").get());
		repository.createItem("Other/added2.txt");
		repository.removeItem(repository.findItem("Processes/b.txt").get());
		repository.save();

		assertTrue(Files.exists(root.resolve("Processes").resolve("added1.txt")));
		assertTrue(Files.exists(root.resolve("Other").resolve("added2.txt")));
		assertTrue(Files.exists(root.resolve("Processes").resolve("a.txt")));
		assertFalse(Files.exists(root.resolve("Processes").resolve("b.txt")));
		assertFalse(Files.exists(root.resolve("Other").resolve("o.txt")));
	}

	@Test
	public void createItemInNewNestedFoldersIsSavedAndFolderCanBeRemoved() {
		Repository repository = open();
		repository.createItem("New/Sub/deep.txt");
		repository.save();
		assertTrue(Files.exists(root.resolve("New").resolve("Sub").resolve("deep.txt")));

		Repository reloaded = open();
		reloaded.removeItem(reloaded.findItem("New").get());
		reloaded.save();

		assertFalse(Files.exists(root.resolve("New")));
	}

	@Test
	public void removingFileAndItsParentFolderBeforeSave() {
		Repository repository = open();
		Item processes = repository.findItem("Processes").get();
		Item a = repository.findItem("Processes/a.txt").get();

		repository.removeItem(a);
		repository.removeItem(processes);
		repository.save();

		assertFalse(Files.exists(root.resolve("Processes")));
		assertFalse(open().findItem("Processes").isPresent());
	}

	@Test
	public void recreateFolderWithSameNameAfterSaveOnReloadedRepository() throws IOException {
		Repository repository = open();
		repository.createItem("Fld/Sub/old.txt");
		repository.createItem("Fld/top.txt");
		repository.save();
		assertTrue(Files.exists(root.resolve("Fld").resolve("Sub").resolve("old.txt")));

		Repository second = open();
		second.removeItem(second.findItem("Fld").get());
		Item recreated = second.createItem("Fld/new.txt");
		try (OutputStream out = recreated.setContent()) {
			out.write("N".getBytes(StandardCharsets.UTF_8));
		}
		second.save();

		assertFalse(Files.exists(root.resolve("Fld").resolve("Sub")));
		assertFalse(Files.exists(root.resolve("Fld").resolve("top.txt")));
		assertEquals("N", new String(Files.readAllBytes(root.resolve("Fld").resolve("new.txt")), StandardCharsets.UTF_8));
		Repository third = open();
		assertFalse(third.findItem("Fld/Sub/old.txt").isPresent());
		assertTrue(third.findItem("Fld/new.txt").isPresent());
	}

	@Test
	public void recreateFolderWithSameNameInSameRepositoryInstance() throws IOException {
		Repository repository = open();
		repository.createItem("Fld/Sub/old.txt");
		repository.createItem("Fld/top.txt");
		repository.save();

		repository.removeItem(repository.findItem("Fld").get());
		Item recreated = repository.createItem("Fld/new.txt");
		try (OutputStream out = recreated.setContent()) {
			out.write("N".getBytes(StandardCharsets.UTF_8));
		}
		repository.save();

		assertFalse(Files.exists(root.resolve("Fld").resolve("Sub")));
		assertFalse(Files.exists(root.resolve("Fld").resolve("top.txt")));
		assertEquals("N", new String(Files.readAllBytes(root.resolve("Fld").resolve("new.txt")), StandardCharsets.UTF_8));
	}

	@Test
	public void recreateFolderWithSameNameAndSameSubitemName() throws IOException {
		Repository repository = open();
		Item old = repository.createItem("Fld/same.txt");
		try (OutputStream out = old.setContent()) {
			out.write("OLD".getBytes(StandardCharsets.UTF_8));
		}
		repository.save();

		Repository second = open();
		second.removeItem(second.findItem("Fld").get());
		Item recreated = second.createItem("Fld/same.txt");
		try (OutputStream out = recreated.setContent()) {
			out.write("NEW".getBytes(StandardCharsets.UTF_8));
		}
		second.save();

		assertEquals("NEW", new String(Files.readAllBytes(root.resolve("Fld").resolve("same.txt")), StandardCharsets.UTF_8));
		assertEquals(1, countChildren(open(), "Fld", "same.txt"));
	}

	private static long countChildren(Repository repository, String folder, String name) {
		return repository.findItem(folder).get().getChildren().stream().filter(c -> c.getName().equals(name)).count();
	}

	@Test
	public void removedItemsAreListedInDeployment() {
		Repository repository = open();

		repository.removeItem(repository.findItem("Processes/a.txt").get());
		repository.removeItem(repository.findItem("Processes/b.txt").get());

		assertEquals(Arrays.asList("/Processes/a.txt", "/Processes/b.txt"),
				repository.getDeployment().getRemovedItems().stream()
						.map(i -> i.getPath().toString().replace('\\', '/')).sorted().collect(Collectors.toList()));
	}
}
