package com.gentics.mesh.core;

import static com.gentics.mesh.util.MeshAssert.assertDeleted;
import static com.gentics.mesh.util.MeshAssert.failingLatch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;

import com.gentics.mesh.api.common.PagingInfo;
import com.gentics.mesh.core.data.TagFamily;
import com.gentics.mesh.core.data.relationship.GraphPermission;
import com.gentics.mesh.core.data.root.TagFamilyRoot;
import com.gentics.mesh.core.rest.tag.TagFamilyResponse;
import com.gentics.mesh.graphdb.Trx;
import com.gentics.mesh.test.AbstractBasicObjectTest;
import com.gentics.mesh.util.InvalidArgumentException;

import io.vertx.ext.web.RoutingContext;

public class TagFamilyTest extends AbstractBasicObjectTest {

	@Test
	@Override
	public void testFindAllVisible() throws InvalidArgumentException {
		try (Trx tx = new Trx(db)) {
			TagFamilyRoot root = meshRoot().getTagFamilyRoot();
			root.findAll(getRequestUser(), new PagingInfo(1, 10));
		}
	}

	@Test
	@Override
	public void testFindAll() throws InvalidArgumentException {
		try (Trx tx = new Trx(db)) {
			TagFamilyRoot root = meshRoot().getTagFamilyRoot();
			List<? extends TagFamily> families = root.findAll();
			assertNotNull(families);
			assertEquals(2, families.size());

			TagFamilyRoot projectTagFamilyRoot = project().getTagFamilyRoot();
			assertNotNull(projectTagFamilyRoot);

			TagFamily projectTagFamily = projectTagFamilyRoot.findByName("colors");
			assertNotNull(projectTagFamily);

			assertNotNull(projectTagFamilyRoot.create("bogus", user()));
			assertEquals(3, projectTagFamilyRoot.findAll().size());
			assertEquals(3, root.findAll().size());
		}
	}

	@Test
	@Override
	public void testRootNode() {
		try (Trx tx = new Trx(db)) {
			TagFamilyRoot root = project().getTagFamilyRoot();
			int nProjectsBefore = root.findAll().size();
			assertNotNull(root.create("test1234556", user()));
			int nProjectsAfter = root.findAll().size();
			assertEquals(nProjectsBefore + 1, nProjectsAfter);
		}
	}

	@Test
	@Override
	public void testFindByName() {
		try (Trx tx = new Trx(db)) {
			TagFamilyRoot root = meshRoot().getTagFamilyRoot();
			assertNotNull(root);
			assertNotNull(root.findByName("colors"));
		}
	}

	@Test
	@Override
	public void testFindByUUID() throws InterruptedException {
		try (Trx tx = new Trx(db)) {
			TagFamilyRoot root = project().getTagFamilyRoot();
			TagFamily tagFamily = tagFamily("colors");

			CountDownLatch latch = new CountDownLatch(1);
			root.findByUuid(tagFamily.getUuid(), rh -> {
				latch.countDown();
			});
			failingLatch(latch);
		}
	}

	@Test
	@Override
	public void testRead() throws IOException {
		try (Trx tx = new Trx(db)) {
			TagFamily tagFamily = tagFamily("colors");
			assertNotNull(tagFamily.getName());
			assertEquals("colors", tagFamily.getName());
			assertNotNull(tagFamily.getEditor());
			assertNotNull(tagFamily.getCreator());
		}
	}

	@Test
	@Override
	public void testCreate() throws IOException {
		try (Trx tx = new Trx(db)) {
			TagFamilyRoot root = project().getTagFamilyRoot();
			TagFamily family = root.create("test", user());
			TagFamily family2 = root.findByName(family.getName());
			assertNotNull(family2);
			assertEquals("test", family2.getName());
			assertEquals(family.getUuid(), family2.getUuid());
		}
	}

	@Test
	@Override
	public void testDelete() {
		try (Trx tx = new Trx(db)) {
			TagFamily tagFamily = tagFamily("colors");
			Map<String, String> uuidToBeDeleted = new HashMap<>();
			uuidToBeDeleted.put("tagFamily", tagFamily.getUuid());
			uuidToBeDeleted.put("tagFamily.red", tag("red").getUuid());
			tagFamily.delete();
			assertDeleted(uuidToBeDeleted);
		}
	}

	@Test
	@Override
	public void testUpdate() {
		try (Trx tx = new Trx(db)) {
			TagFamily tagFamily = tagFamily("colors");
			tagFamily.setName("new Name");
			assertEquals("new Name", tagFamily.getName());
		}

	}

	@Test
	@Override
	public void testReadPermission() {
		try (Trx tx = new Trx(db)) {
			TagFamily tagFamily = project().getTagFamilyRoot().create("newProject", user());
			testPermission(GraphPermission.READ_PERM, tagFamily);
		}
	}

	@Test
	@Override
	public void testDeletePermission() {
		try (Trx tx = new Trx(db)) {
			TagFamily tagFamily = project().getTagFamilyRoot().create("newProject", user());
			testPermission(GraphPermission.DELETE_PERM, tagFamily);
		}
	}

	@Test
	@Override
	public void testUpdatePermission() {
		try (Trx tx = new Trx(db)) {
			TagFamily tagFamily = project().getTagFamilyRoot().create("newProject", user());
			testPermission(GraphPermission.UPDATE_PERM, tagFamily);
		}
	}

	@Test
	@Override
	public void testCreatePermission() {
		try (Trx tx = new Trx(db)) {
			TagFamily tagFamily = project().getTagFamilyRoot().create("newProject", user());
			testPermission(GraphPermission.CREATE_PERM, tagFamily);
		}
	}

	@Test
	@Override
	public void testTransformation() throws InterruptedException {
		try (Trx tx = new Trx(db)) {
			TagFamily tagFamily = tagFamily("colors");
			CountDownLatch latch = new CountDownLatch(1);
			RoutingContext rc = getMockedRoutingContext("");
			tagFamily.transformToRest(rc, rh -> {
				assertNotNull(rh.result());
				TagFamilyResponse response = rh.result();
				assertEquals(tagFamily.getName(), response.getName());
				assertEquals(tagFamily.getUuid(), response.getUuid());
				latch.countDown();
			});
			failingLatch(latch);
		}
	}

	@Test
	@Override
	public void testCreateDelete() throws InterruptedException {
		try (Trx tx = new Trx(db)) {
			TagFamilyRoot root = project().getTagFamilyRoot();
			TagFamily tagFamily = root.create("test123", user());
			assertNotNull(tagFamily);
			String uuid = tagFamily.getUuid();
			CountDownLatch latch = new CountDownLatch(2);
			root.findByUuid(uuid, rh -> {
				assertNotNull(rh.result());
				latch.countDown();
			});
			tagFamily.delete();
			// TODO check for attached nodes
			meshRoot().getProjectRoot().findByUuid(uuid, rh -> {
				assertNull(rh.result());
				latch.countDown();
			});
			failingLatch(latch);
		}
	}

	@Test
	@Override
	public void testCRUDPermissions() {
		try (Trx tx = new Trx(db)) {
			TagFamilyRoot root = project().getTagFamilyRoot();
			TagFamily tagFamily = root.create("test123", user());
			assertFalse(user().hasPermission(tagFamily, GraphPermission.CREATE_PERM));
			user().addCRUDPermissionOnRole(root, GraphPermission.CREATE_PERM, tagFamily);
			assertTrue(user().hasPermission(tagFamily, GraphPermission.CREATE_PERM));
		}
	}

}
