package com.gentics.mesh.core.data.impl;

import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_LANGUAGE;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_MICROSCHEMA_ROOT;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_NODE_ROOT;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_RELEASE_ROOT;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_ROOT_NODE;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_SCHEMA_ROOT;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_TAGFAMILY_ROOT;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_TAG_ROOT;
import static com.gentics.mesh.core.data.search.SearchQueueEntryAction.DELETE_ACTION;
import static com.gentics.mesh.core.data.search.SearchQueueEntryAction.STORE_ACTION;
import static com.gentics.mesh.core.rest.error.Errors.conflict;
import static com.gentics.mesh.core.rest.error.Errors.error;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

import java.util.List;
import java.util.Set;

import javax.naming.InvalidNameException;

import com.gentics.mesh.Mesh;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.ContainerType;
import com.gentics.mesh.core.data.Language;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.Release;
import com.gentics.mesh.core.data.Role;
import com.gentics.mesh.core.data.User;
import com.gentics.mesh.core.data.generic.AbstractMeshCoreVertex;
import com.gentics.mesh.core.data.generic.MeshVertexImpl;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.node.impl.NodeImpl;
import com.gentics.mesh.core.data.relationship.GraphPermission;
import com.gentics.mesh.core.data.root.MeshRoot;
import com.gentics.mesh.core.data.root.MicroschemaContainerRoot;
import com.gentics.mesh.core.data.root.NodeRoot;
import com.gentics.mesh.core.data.root.ReleaseRoot;
import com.gentics.mesh.core.data.root.SchemaContainerRoot;
import com.gentics.mesh.core.data.root.TagFamilyRoot;
import com.gentics.mesh.core.data.root.TagRoot;
import com.gentics.mesh.core.data.root.impl.NodeRootImpl;
import com.gentics.mesh.core.data.root.impl.ProjectMicroschemaContainerRootImpl;
import com.gentics.mesh.core.data.root.impl.ProjectSchemaContainerRootImpl;
import com.gentics.mesh.core.data.root.impl.ReleaseRootImpl;
import com.gentics.mesh.core.data.root.impl.TagFamilyRootImpl;
import com.gentics.mesh.core.data.root.impl.TagRootImpl;
import com.gentics.mesh.core.data.schema.SchemaContainer;
import com.gentics.mesh.core.data.schema.SchemaContainerVersion;
import com.gentics.mesh.core.data.search.SearchQueueBatch;
import com.gentics.mesh.core.data.search.SearchQueueEntryAction;
import com.gentics.mesh.core.rest.project.ProjectReference;
import com.gentics.mesh.core.rest.project.ProjectResponse;
import com.gentics.mesh.core.rest.project.ProjectUpdateRequest;
import com.gentics.mesh.dagger.MeshInternal;
import com.gentics.mesh.etc.RouterStorage;
import com.gentics.mesh.graphdb.spi.Database;
import com.gentics.mesh.util.ETag;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @see Project
 */
public class ProjectImpl extends AbstractMeshCoreVertex<ProjectResponse, Project> implements Project {

	private static final Logger log = LoggerFactory.getLogger(ProjectImpl.class);

	public static void init(Database database) {
		// TODO index to name + unique constraint
		database.addVertexType(ProjectImpl.class, MeshVertexImpl.class);
		database.addVertexIndex(ProjectImpl.class, true, "name");
	}

	@Override
	public ProjectReference transformToReference() {
		return new ProjectReference().setName(getName()).setUuid(getUuid());
	}

	@Override
	public String getType() {
		return Project.TYPE;
	}

	@Override
	public String getName() {
		return getProperty("name");
	}

	@Override
	public void addLanguage(Language language) {
		setUniqueLinkOutTo(language.getImpl(), HAS_LANGUAGE);
	}

	@Override
	public List<? extends Language> getLanguages() {
		return out(HAS_LANGUAGE).toListExplicit(LanguageImpl.class);
	}

	@Override
	public void removeLanguage(Language language) {
		unlinkOut(language.getImpl(), HAS_LANGUAGE);
	}

	@Override
	public void setName(String name) {
		setProperty("name", name);
	}

	@Override
	public TagFamilyRoot getTagFamilyRoot() {
		TagFamilyRoot root = out(HAS_TAGFAMILY_ROOT).nextOrDefaultExplicit(TagFamilyRootImpl.class, null);
		if (root == null) {
			root = getGraph().addFramedVertex(TagFamilyRootImpl.class);
			linkOut(root.getImpl(), HAS_TAGFAMILY_ROOT);
		}
		return root;
	}

	@Override
	public SchemaContainerRoot getSchemaContainerRoot() {
		SchemaContainerRoot root = out(HAS_SCHEMA_ROOT).nextOrDefaultExplicit(ProjectSchemaContainerRootImpl.class, null);
		if (root == null) {
			root = getGraph().addFramedVertex(ProjectSchemaContainerRootImpl.class);
			linkOut(root.getImpl(), HAS_SCHEMA_ROOT);
		}
		return root;
	}

	@Override
	public MicroschemaContainerRoot getMicroschemaContainerRoot() {
		MicroschemaContainerRoot root = out(HAS_MICROSCHEMA_ROOT).nextOrDefaultExplicit(ProjectMicroschemaContainerRootImpl.class, null);
		if (root == null) {
			root = getGraph().addFramedVertex(ProjectMicroschemaContainerRootImpl.class);
			linkOut(root.getImpl(), HAS_MICROSCHEMA_ROOT);
		}
		return root;
	}

	@Override
	public Node getBaseNode() {
		return out(HAS_ROOT_NODE).nextOrDefaultExplicit(NodeImpl.class, null);
	}

	@Override
	public TagRoot getTagRoot() {
		TagRoot root = out(HAS_TAG_ROOT).nextOrDefaultExplicit(TagRootImpl.class, null);
		if (root == null) {
			root = getGraph().addFramedVertex(TagRootImpl.class);
			linkOut(root.getImpl(), HAS_TAG_ROOT);
		}
		return root;
	}

	@Override
	public NodeRoot getNodeRoot() {
		NodeRoot root = out(HAS_NODE_ROOT).nextOrDefaultExplicit(NodeRootImpl.class, null);
		if (root == null) {
			root = getGraph().addFramedVertex(NodeRootImpl.class);
			linkOut(root.getImpl(), HAS_NODE_ROOT);
		}
		return root;
	}

	@Override
	public void setBaseNode(Node baseNode) {
		linkOut(baseNode.getImpl(), HAS_ROOT_NODE);
	}

	@Override
	public ProjectResponse transformToRestSync(InternalActionContext ac, int level, String... languageTags) {
		ProjectResponse restProject = new ProjectResponse();
		restProject.setName(getName());
		restProject.setRootNodeUuid(getBaseNode().getUuid());

		fillCommonRestFields(ac, restProject);
		setRolePermissions(ac, restProject);

		return restProject;
	}

	@Override
	public Node createBaseNode(User creator, SchemaContainerVersion schemaContainerVersion) {
		Node baseNode = getBaseNode();
		if (baseNode == null) {
			baseNode = getGraph().addFramedVertex(NodeImpl.class);
			baseNode.setSchemaContainer(schemaContainerVersion.getSchemaContainer());
			baseNode.setProject(this);
			baseNode.setCreated(creator);
			Language language = MeshInternal.get().boot().languageRoot().findByLanguageTag(Mesh.mesh().getOptions().getDefaultLanguage());
			baseNode.createGraphFieldContainer(language, getLatestRelease(), creator);
			setBaseNode(baseNode);
			// Add the node to the aggregation nodes
			getNodeRoot().addNode(baseNode);
			MeshInternal.get().boot().nodeRoot().addNode(baseNode);
		}
		return baseNode;
	}

	@Override
	public void delete(SearchQueueBatch batch) {
		if (log.isDebugEnabled()) {
			log.debug("Deleting project {" + getName() + "}");
		}
		batch.addEntry(this, DELETE_ACTION);

		RouterStorage.getIntance().removeProjectRouter(getName());
		getBaseNode().delete(true, batch);
		getTagFamilyRoot().delete(batch);
		getNodeRoot().delete(batch);

		for (SchemaContainer container : getSchemaContainerRoot().findAll()) {
			getSchemaContainerRoot().removeSchemaContainer(container);
		}
		getSchemaContainerRoot().delete(batch);
		reload();
		getVertex().remove();
	}

	@Override
	public Project update(InternalActionContext ac, SearchQueueBatch batch) {
		ProjectUpdateRequest requestModel = ac.fromJson(ProjectUpdateRequest.class);

		String oldName = getName();
		String newName = requestModel.getName();
		if (shouldUpdate(newName, oldName)) {
			// Check for conflicting project name
			Project projectWithSameName = MeshRoot.getInstance().getProjectRoot().findByName(newName);
			if (projectWithSameName != null && !projectWithSameName.getUuid().equals(getUuid())) {
				throw conflict(projectWithSameName.getUuid(), newName, "project_conflicting_name");
			}

			RouterStorage routerStorage = MeshInternal.get().routerStorage();
			if (routerStorage.getCoreRouters().containsKey(newName)) {
				throw error(BAD_REQUEST, "project_error_name_already_reserved", newName);
			}

			setName(newName);
			routerStorage.removeProjectRouter(oldName);

			// Old router was removed. Now lets add the new one and revert the change if an error occures.
			try {
				routerStorage.addProjectRouter(newName);
			} catch (InvalidNameException e) {
				// Try to restore the old state. Transaction will be rolled back later.
				try {
					routerStorage.addProjectRouter(oldName);
				} catch (InvalidNameException e1) {
					//TODO i18n
					throw error(INTERNAL_SERVER_ERROR, "Error while restoring project router for name {" + oldName + "} during rollback", e);
				}
				// TODO i18n
				throw error(BAD_REQUEST, "Error while adding new router for project name {" + newName + "}", e);
			}
			setEditor(ac.getUser());
			setLastEditedTimestamp();
			addIndexBatchEntry(batch, STORE_ACTION, true);
		}
		return this;
	}

	@Override
	public void applyPermissions(Role role, boolean recursive, Set<GraphPermission> permissionsToGrant, Set<GraphPermission> permissionsToRevoke) {
		if (recursive) {
			getSchemaContainerRoot().applyPermissions(role, recursive, permissionsToGrant, permissionsToRevoke);
			getTagFamilyRoot().applyPermissions(role, recursive, permissionsToGrant, permissionsToRevoke);
			getNodeRoot().applyPermissions(role, recursive, permissionsToGrant, permissionsToRevoke);
		}
		super.applyPermissions(role, recursive, permissionsToGrant, permissionsToRevoke);
	}

	@Override
	public void addRelatedEntries(SearchQueueBatch batch, SearchQueueEntryAction action) {
		String baseNodeUuid = getBaseNode().getUuid();
		List<? extends Release> releases = getReleaseRoot().findAll();
		for (Node node : getNodeRoot().findAll()) {
			if (baseNodeUuid.equals(node.getUuid())) {
				continue;
			}
			releases.forEach(release -> {
				node.getGraphFieldContainers(release, ContainerType.DRAFT).forEach(container -> {
					container.addIndexBatchEntry(batch, STORE_ACTION, release.getUuid(), ContainerType.DRAFT);
				});
				node.getGraphFieldContainers(release, ContainerType.PUBLISHED).forEach(container -> {
					container.addIndexBatchEntry(batch, STORE_ACTION, release.getUuid(), ContainerType.PUBLISHED);
				});
			});
		}
	}

	@Override
	public Release getInitialRelease() {
		return getReleaseRoot().getInitialRelease();
	}

	@Override
	public Release getLatestRelease() {
		return getReleaseRoot().getLatestRelease();
	}

	@Override
	public ReleaseRoot getReleaseRoot() {
		ReleaseRoot root = out(HAS_RELEASE_ROOT).has(ReleaseRootImpl.class).nextOrDefaultExplicit(ReleaseRootImpl.class, null);
		if (root == null) {
			root = getGraph().addFramedVertex(ReleaseRootImpl.class);
			linkOut(root.getImpl(), HAS_RELEASE_ROOT);
		}
		return root;
	}

	@Override
	public String getETag(InternalActionContext ac) {
		return ETag.hash(getUuid() + "-" + getLastEditedTimestamp());
	}

	@Override
	public String getAPIPath(InternalActionContext ac) {
		return "/api/v1/projects/" + getUuid();
	}
}
