package com.meluzin.tibcobwutils.deploymentrepository.structure.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.meluzin.tibcobwutils.deploymentrepository.structure.Alias;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Config;
import com.meluzin.tibcobwutils.deploymentrepository.structure.Item;
import com.meluzin.tibcobwutils.deploymentrepository.structure.ItemSourceType;

public class InMemoryChangesTest {
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();
	private Path root;
	private InMemoryChangesImpl changes;

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
		Files.createDirectories(root.resolve("Processes"));
		write(root.resolve("Processes").resolve("a.txt"), "A");
		write(root.resolve("Processes").resolve("b.txt"), "B");
		changes = new InMemoryChangesImpl(new DeploymentImpl(root, EMPTY_CONFIG));
	}

	private static void write(Path path, String text) throws IOException {
		Files.write(path, text.getBytes(StandardCharsets.UTF_8));
	}

	private static String read(Item item) throws IOException {
		try (java.io.InputStream in = item.getContent()) {
			return new String(org.apache.commons.io.IOUtils.toByteArray(in), StandardCharsets.UTF_8);
		}
	}

	private Item child(Item parent, String name) {
		return parent.getChild(name).get();
	}

	@Test
	public void sourceTypeIsMemory() {
		assertEquals(ItemSourceType.Memory, changes.getType());
	}

	@Test
	public void untouchedItemsDelegateToOriginalAndAreNotChanged() throws IOException {
		Item processes = child(changes.getRootFolder(), "Processes");
		Item a = child(processes, "a.txt");

		assertFalse(((InMemoryChangesItem) changes.getRootFolder()).isChanged());
		assertFalse(((InMemoryChangesItem) a).isChanged());
		assertEquals("A", read(a));
		assertEquals(2, processes.getChildren().size());
	}

	@Test
	public void setContentMarksItemAndAncestorsChangedWithoutTouchingDisk() throws IOException {
		Item processes = child(changes.getRootFolder(), "Processes");
		Item a = child(processes, "a.txt");

		try (OutputStream out = a.setContent()) {
			out.write("changed".getBytes(StandardCharsets.UTF_8));
		}

		assertTrue(((InMemoryChangesItem) a).isChanged());
		assertTrue(((InMemoryChangesItem) a).getParent().isChanged());
		assertTrue(((InMemoryChangesItem) changes.getRootFolder()).isChanged());
		assertEquals("changed", read(a));
		assertEquals("A", new String(Files.readAllBytes(root.resolve("Processes").resolve("a.txt")), StandardCharsets.UTF_8));
	}

	@Test
	public void createItemAddsChildToChangedParent() {
		Item processes = child(changes.getRootFolder(), "Processes");

		Item created = changes.createItem(processes, "new.txt", false);

		assertTrue(((InMemoryChangesItem) created).isChanged());
		assertTrue(((InMemoryChangesItem) processes).isChanged());
		assertTrue(processes.getChild("new.txt").isPresent());
		assertEquals(3, processes.getChildren().size());
		assertFalse(Files.exists(root.resolve("Processes").resolve("new.txt")));
	}

	@Test
	public void removeItemRecordsItAndKeepsFileOnDisk() {
		Item processes = child(changes.getRootFolder(), "Processes");
		Item a = child(processes, "a.txt");

		changes.removeItem(a);

		assertEquals(1, changes.getRemovedItems().size());
		assertTrue(changes.getRemovedItems().contains(a));
		assertTrue(((InMemoryChangesItem) processes).isChanged());
		assertTrue(Files.exists(root.resolve("Processes").resolve("a.txt")));
	}

	@Test
	public void removedItemsSetIsUnmodifiable() {
		Item a = child(child(changes.getRootFolder(), "Processes"), "a.txt");
		changes.removeItem(a);

		try {
			changes.getRemovedItems().clear();
			org.junit.Assert.fail("expected UnsupportedOperationException");
		} catch (UnsupportedOperationException expected) {
		}
	}

	@Test
	public void removeItemByParentAndNameDeletesFileFromDisk() {
		Item processes = child(changes.getRootFolder(), "Processes");

		changes.removeItem(processes, "a.txt", false);

		assertFalse(Files.exists(root.resolve("Processes").resolve("a.txt")));
		assertTrue(Files.exists(root.resolve("Processes").resolve("b.txt")));
	}
}
