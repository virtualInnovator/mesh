package com.gentics.mesh.core.data.node.impl;

import static com.gentics.mesh.core.data.relationship.GraphRelationships.ASSIGNED_TO_PROJECT;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_FIELD_CONTAINER;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_PARENT_NODE;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_SCHEMA_CONTAINER;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_TAG;
import static com.gentics.mesh.core.data.search.SearchQueueEntryAction.UPDATE_ACTION;
import static com.gentics.mesh.util.VerticleHelper.processOrFail2;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.gentics.mesh.Mesh;
import com.gentics.mesh.api.common.PagingInfo;
import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.core.Page;
import com.gentics.mesh.core.data.GraphFieldContainer;
import com.gentics.mesh.core.data.Language;
import com.gentics.mesh.core.data.MeshAuthUser;
import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.Role;
import com.gentics.mesh.core.data.SchemaContainer;
import com.gentics.mesh.core.data.Tag;
import com.gentics.mesh.core.data.TagFamily;
import com.gentics.mesh.core.data.User;
import com.gentics.mesh.core.data.generic.GenericFieldContainerNode;
import com.gentics.mesh.core.data.impl.NodeGraphFieldContainerImpl;
import com.gentics.mesh.core.data.impl.ProjectImpl;
import com.gentics.mesh.core.data.impl.SchemaContainerImpl;
import com.gentics.mesh.core.data.impl.TagImpl;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.relationship.GraphPermission;
import com.gentics.mesh.core.data.root.impl.MeshRootImpl;
import com.gentics.mesh.core.data.search.SearchQueue;
import com.gentics.mesh.core.data.search.SearchQueueBatch;
import com.gentics.mesh.core.data.search.SearchQueueEntryAction;
import com.gentics.mesh.core.data.service.ServerSchemaStorage;
import com.gentics.mesh.core.rest.error.HttpStatusCodeErrorException;
import com.gentics.mesh.core.rest.node.BinaryProperties;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.node.NodeUpdateRequest;
import com.gentics.mesh.core.rest.schema.FieldSchema;
import com.gentics.mesh.core.rest.schema.Schema;
import com.gentics.mesh.core.rest.tag.TagFamilyTagGroup;
import com.gentics.mesh.core.rest.tag.TagReference;
import com.gentics.mesh.core.rest.user.NodeReferenceImpl;
import com.gentics.mesh.error.MeshSchemaException;
import com.gentics.mesh.etc.MeshSpringConfiguration;
import com.gentics.mesh.graphdb.Trx;
import com.gentics.mesh.graphdb.spi.Database;
import com.gentics.mesh.handler.InternalActionContext;
import com.gentics.mesh.json.JsonUtil;
import com.gentics.mesh.util.InvalidArgumentException;
import com.gentics.mesh.util.TraversalHelper;
import com.gentics.mesh.util.UUIDUtil;
import com.syncleus.ferma.traversals.VertexTraversal;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import rx.Observable;

public class NodeImpl extends GenericFieldContainerNode<NodeResponse>implements Node {

	private static final Logger log = LoggerFactory.getLogger(NodeImpl.class);

	private static final String BINARY_FILESIZE_PROPERTY_KEY = "binaryFileSize";

	private static final String BINARY_FILENAME_PROPERTY_KEY = "binaryFilename";

	private static final String BINARY_SHA512SUM_PROPERTY_KEY = "binarySha512Sum";

	private static final String BINARY_CONTENT_TYPE_PROPERTY_KEY = "binaryContentType";

	private static final String BINARY_IMAGE_DPI_PROPERTY_KEY = "binaryImageDPI";

	private static final String BINARY_IMAGE_WIDTH_PROPERTY_KEY = "binaryImageWidth";

	private static final String BINARY_IMAGE_HEIGHT_PROPERTY_KEY = "binaryImageHeight";

	private static final String PUBLISHED_PROPERTY_KEY = "published";

	@Override
	public String getType() {
		return Node.TYPE;
	}

	public List<? extends Tag> getTags() {
		return out(HAS_TAG).has(TagImpl.class).toListExplicit(TagImpl.class);
	}

	@Override
	public List<? extends NodeGraphFieldContainer> getGraphFieldContainers() {
		return out(HAS_FIELD_CONTAINER).has(NodeGraphFieldContainerImpl.class).toListExplicit(NodeGraphFieldContainerImpl.class);
	}

	@Override
	public NodeGraphFieldContainer getGraphFieldContainer(Language language) {
		return getGraphFieldContainer(language, NodeGraphFieldContainerImpl.class);
	}

	public NodeGraphFieldContainer getOrCreateGraphFieldContainer(Language language) {
		return getOrCreateGraphFieldContainer(language, NodeGraphFieldContainerImpl.class);
	}

	@Override
	public void addTag(Tag tag) {
		linkOut(tag.getImpl(), HAS_TAG);
	}

	@Override
	public void removeTag(Tag tag) {
		unlinkOut(tag.getImpl(), HAS_TAG);
	}

	@Override
	public void createLink(Node to) {
		// TODO maybe extract information about link start and end to speedup rendering of page with links
		// Linked link = new Linked(this, page);
		// this.links.add(link);
	}

	public void setSchemaContainer(SchemaContainer schema) {
		setLinkOut(schema.getImpl(), HAS_SCHEMA_CONTAINER);
	}

	public SchemaContainer getSchemaContainer() {
		return out(HAS_SCHEMA_CONTAINER).has(SchemaContainerImpl.class).nextOrDefaultExplicit(SchemaContainerImpl.class, null);
	}

	@Override
	public Schema getSchema() {
		return getSchemaContainer().getSchema();
	}

	@Override
	public List<? extends Node> getChildren() {
		return in(HAS_PARENT_NODE).has(NodeImpl.class).toListExplicit(NodeImpl.class);
	}

	@Override
	public Node getParentNode() {
		return out(HAS_PARENT_NODE).has(NodeImpl.class).nextOrDefaultExplicit(NodeImpl.class, null);
	}

	@Override
	public void setParentNode(Node parent) {
		setLinkOut(parent.getImpl(), HAS_PARENT_NODE);
	}

	@Override
	public Project getProject() {
		return out(ASSIGNED_TO_PROJECT).has(ProjectImpl.class).nextOrDefaultExplicit(ProjectImpl.class, null);
	}

	@Override
	public void setProject(Project project) {
		setLinkOut(project.getImpl(), ASSIGNED_TO_PROJECT);
	}

	@Override
	public Node create(User creator, SchemaContainer schemaContainer, Project project) {
		Node node = BootstrapInitializer.getBoot().nodeRoot().create(creator, schemaContainer, project);
		node.setParentNode(this);
		return node;
	}

	private String getLanguageInfo(List<String> languageTags) {
		Iterator<String> it = languageTags.iterator();

		String langInfo = "[";
		while (it.hasNext()) {
			langInfo += it.next();
			if (it.hasNext()) {
				langInfo += ",";
			}
		}
		langInfo += "]";
		return langInfo;
	}

	@Override
	public Node transformToRest(InternalActionContext ac, Handler<AsyncResult<NodeResponse>> handler) {
		Database db = MeshSpringConfiguration.getInstance().database();
		Set<ObservableFuture<Void>> futures = new HashSet<>();

		//TODO use notrx here
		db.asyncNoTrx(trx -> {
			NodeResponse restNode = new NodeResponse();
			fillRest(restNode, ac);
			//trx.failure();
			SchemaContainer container = getSchemaContainer();
			if (container == null) {
				trx.fail(new HttpStatusCodeErrorException(BAD_REQUEST, "The schema container for node {" + getUuid() + "} could not be found."));
			}
			restNode.setPublished(isPublished());

			try {
				Schema schema = container.getSchema();
				if (schema == null) {
					trx.fail(new HttpStatusCodeErrorException(BAD_REQUEST, "The schema for node {" + getUuid() + "} could not be found."));
				} else {
					restNode.setDisplayField(schema.getDisplayField());
					// Load the children
					if (schema.isFolder()) {
						// //TODO handle uuid
						// //TODO handle expand
						List<String> children = new ArrayList<>();
						// //TODO check permissions
						for (Node child : getChildren()) {
							children.add(child.getUuid());
						}
						restNode.setContainer(true);
						restNode.setChildren(children);
					}
					if (schema.isBinary()) {
						restNode.setFileName(getBinaryFileName());
						BinaryProperties binaryProperties = new BinaryProperties();
						binaryProperties.setMimeType(getBinaryContentType());
						binaryProperties.setFileSize(getBinaryFileSize());
						binaryProperties.setSha512sum(getBinarySHA512Sum());
						// TODO determine whether file is an image
						// binaryProperties.setDpi(getImageDpi());
						getBinaryImageDPI();
						getBinaryImageHeight();
						getBinaryImageWidth();
						// binaryProperties.setHeight(getImageHeight());
						// binaryProperties.setWidth(getImageWidth());
						restNode.setBinaryProperties(binaryProperties);
					}
				}

				// Schema reference
				SchemaContainer schemaContainer = getSchemaContainer();
				if (schemaContainer != null) {
					ObservableFuture<Void> obsSchemaReference = RxHelper.observableFuture();
					futures.add(obsSchemaReference);
					schemaContainer.transformToReference(ac, rh -> {
						if (rh.succeeded()) {
							restNode.setSchema(rh.result());
							obsSchemaReference.toHandler().handle(Future.succeededFuture());
						} else {
							obsSchemaReference.toHandler().handle(Future.failedFuture(rh.cause()));
						}
					});
				}

				// Parent node reference
				Node parentNode = getParentNode();
				if (parentNode != null) {
					ObservableFuture<Void> obsParentNodeReference = RxHelper.observableFuture();
					futures.add(obsParentNodeReference);
					parentNode.transformToReference(ac, rh -> {
						if (rh.succeeded()) {
							restNode.setParentNode(rh.result());
							obsParentNodeReference.toHandler().handle(Future.succeededFuture());
						} else {
							obsParentNodeReference.toHandler().handle(Future.failedFuture(rh.cause()));
						}
					});
				}

				NodeGraphFieldContainer fieldContainer = findNextMatchingFieldContainer(ac);
				restNode.setAvailableLanguages(getAvailableLanguageNames());

				if (fieldContainer == null) {
					List<String> languageTags = ac.getSelectedLanguageTags();
					String langInfo = getLanguageInfo(languageTags);
					log.info("The fields for node {" + getUuid() + "} can't be populated since the node has no matching language for the languages {"
							+ langInfo + "}. Fields will be empty.");
					// throw new HttpStatusCodeErrorException(400, getI18n().get(rc, "node_no_language_found", langInfo));
				} else {
					restNode.setLanguage(fieldContainer.getLanguage().getLanguageTag());
					List<String> fieldsToExpand = ac.getExpandedFieldnames();
					for (FieldSchema fieldEntry : schema.getFields()) {
						boolean expandField = fieldsToExpand.contains(fieldEntry.getName());
						com.gentics.mesh.core.rest.node.field.Field restField = fieldContainer.getRestFieldFromGraph(ac, fieldEntry.getName(),
								fieldEntry, expandField);
						if (fieldEntry.isRequired() && restField == null) {
							/* TODO i18n */
							trx.fail(new HttpStatusCodeErrorException(BAD_REQUEST, "The field {" + fieldEntry.getName()
									+ "} is a required field but it could not be found in the node. Please add the field using an update call or change the field schema and remove the required flag."));
						}
						if (restField == null) {
							log.info("Field for key {" + fieldEntry.getName() + "} could not be found. Ignoring the field.");
						} else {
							restNode.getFields().put(fieldEntry.getName(), restField);
						}
					}
				}

				// Tags
				for (Tag tag : getTags(ac)) {
					TagFamily tagFamily = tag.getTagFamily();
					String tagFamilyName = tagFamily.getName();
					String tagFamilyUuid = tagFamily.getUuid();
					TagReference reference = tag.tansformToTagReference();
					TagFamilyTagGroup group = restNode.getTags().get(tagFamilyName);
					if (group == null) {
						group = new TagFamilyTagGroup();
						group.setUuid(tagFamilyUuid);
						restNode.getTags().put(tagFamilyName, group);
					}
					group.getItems().add(reference);
				}
			} catch (InvalidArgumentException e) {
				// TODO i18n
				trx.fail(new HttpStatusCodeErrorException(BAD_REQUEST, "Could not transform tags"));
			}
			Observable.merge(futures).last().subscribe(lastItem -> {
				trx.complete(restNode);
			} , error -> {
				trx.fail(error);
			});

		} , (AsyncResult<NodeResponse> rh) -> {
			handler.handle(rh);
		});

		return this;

	}

	@Override
	public Node transformToReference(InternalActionContext ac, Handler<AsyncResult<NodeReferenceImpl>> handler) {
		NodeReferenceImpl nodeReference = new NodeReferenceImpl();
		nodeReference.setUuid(getUuid());
		nodeReference.setDisplayName(getDisplayName(ac));
		getSchemaContainer().transformToReference(ac, rh -> {
			nodeReference.setSchema(rh.result());
		});
		handler.handle(Future.succeededFuture(nodeReference));
		return this;
	}

	@Override
	public NodeGraphFieldContainer findNextMatchingFieldContainer(InternalActionContext ac) {
		NodeGraphFieldContainer fieldContainer = null;
		List<String> languageTags = ac.getSelectedLanguageTags();
		for (String languageTag : languageTags) {
			Language language = MeshRootImpl.getInstance().getLanguageRoot().findByLanguageTag(languageTag);
			if (language == null) {
				throw new HttpStatusCodeErrorException(BAD_REQUEST, ac.i18n("error_language_not_found", languageTag));
			}
			fieldContainer = getGraphFieldContainer(language);
			// We found a container for one of the languages
			if (fieldContainer != null) {
				break;
			}
		}
		return fieldContainer;
	}

	@Override
	public Integer getBinaryImageWidth() {
		return getProperty(BINARY_IMAGE_WIDTH_PROPERTY_KEY);
	}

	@Override
	public void setBinaryImageWidth(Integer width) {
		setProperty(BINARY_IMAGE_WIDTH_PROPERTY_KEY, width);
	}

	@Override
	public Integer getBinaryImageHeight() {
		return getProperty(BINARY_IMAGE_HEIGHT_PROPERTY_KEY);
	}

	@Override
	public void setBinaryImageHeight(Integer heigth) {
		setProperty(BINARY_IMAGE_HEIGHT_PROPERTY_KEY, heigth);
	}

	@Override
	public Integer getBinaryImageDPI() {
		return getProperty(BINARY_IMAGE_DPI_PROPERTY_KEY);
	}

	@Override
	public void setBinaryImageDPI(Integer dpi) {
		setProperty(BINARY_IMAGE_DPI_PROPERTY_KEY, dpi);
	}

	@Override
	public String getBinarySHA512Sum() {
		return getProperty(BINARY_SHA512SUM_PROPERTY_KEY);
	}

	@Override
	public void setBinarySHA512Sum(String sha512HashSum) {
		setProperty(BINARY_SHA512SUM_PROPERTY_KEY, sha512HashSum);
	}

	@Override
	public long getBinaryFileSize() {
		Long size = getProperty(BINARY_FILESIZE_PROPERTY_KEY);
		return size == null ? 0 : size;
	}

	@Override
	public void setBinaryFileSize(long sizeInBytes) {
		setProperty(BINARY_FILESIZE_PROPERTY_KEY, sizeInBytes);
	}

	@Override
	public void setBinaryFileName(String filenName) {
		setProperty(BINARY_FILENAME_PROPERTY_KEY, filenName);
	}

	@Override
	public String getBinaryFileName() {
		return getProperty(BINARY_FILENAME_PROPERTY_KEY);
	}

	@Override
	public String getBinaryContentType() {
		return getProperty(BINARY_CONTENT_TYPE_PROPERTY_KEY);
	}

	@Override
	public void setBinaryContentType(String contentType) {
		setProperty(BINARY_CONTENT_TYPE_PROPERTY_KEY, contentType);
	}

	@Override
	public Future<Buffer> getBinaryFileBuffer() {
		Future<Buffer> future = Future.future();
		Mesh.vertx().fileSystem().readFile(getFilePath(), rh -> {
			if (rh.succeeded()) {
				future.complete(rh.result());
			} else {
				future.fail(rh.cause());
			}
		});
		return future;
	}

	@Override
	public List<String> getAvailableLanguageNames() {
		// TODO Auto-generated method stub
		// TODO set language and all languages
		return null;
	}

	@Override
	public void delete() {
		// Delete subfolders
		if (log.isDebugEnabled()) {
			log.debug("Deleting node {" + getUuid() + "}");
		}
		for (Node child : getChildren()) {
			child.delete();
		}
		for (NodeGraphFieldContainer container : getGraphFieldContainers()) {
			container.delete();
		}
		getElement().remove();
	}

	@Override
	public Page<? extends Node> getChildren(MeshAuthUser requestUser, List<String> languageTags, PagingInfo pagingInfo)
			throws InvalidArgumentException {
		// TODO add permissions
		VertexTraversal<?, ?, ?> traversal = in(HAS_PARENT_NODE).has(NodeImpl.class);
		VertexTraversal<?, ?, ?> countTraversal = in(HAS_PARENT_NODE).has(NodeImpl.class);
		return TraversalHelper.getPagedResult(traversal, countTraversal, pagingInfo, NodeImpl.class);
	}

	@Override
	public Page<? extends Tag> getTags(InternalActionContext ac) throws InvalidArgumentException {
		// TODO add permissions
		VertexTraversal<?, ?, ?> traversal = out(HAS_TAG).has(TagImpl.class);
		VertexTraversal<?, ?, ?> countTraversal = out(HAS_TAG).has(TagImpl.class);
		return TraversalHelper.getPagedResult(traversal, countTraversal, ac.getPagingInfo(), TagImpl.class);
	}

	@Override
	public String getFilePath() {
		File folder = new File(Mesh.mesh().getOptions().getUploadOptions().getDirectory(), getSegmentedPath());
		File binaryFile = new File(folder, getUuid() + ".bin");
		return binaryFile.getAbsolutePath();
	}

	@Override
	public String getSegmentedPath() {
		String uuid = getUuid();
		String[] parts = uuid.split("(?<=\\G.{4})");
		StringBuffer buffer = new StringBuffer();
		buffer.append('/');
		for (String part : parts) {
			buffer.append(part + '/');
		}
		return buffer.toString();
	}

	@Override
	public void applyPermissions(Role role, boolean recursive, Set<GraphPermission> permissionsToGrant, Set<GraphPermission> permissionsToRevoke) {
		if (recursive) {
			for (Node child : getChildren()) {
				child.applyPermissions(role, recursive, permissionsToGrant, permissionsToRevoke);
			}
		}
		super.applyPermissions(role, recursive, permissionsToGrant, permissionsToRevoke);
	}

	@Override
	public String getDisplayName(InternalActionContext ac) {
		String displayFieldName = null;
		try {
			GraphFieldContainer container = findNextMatchingFieldContainer(ac);
			if (container == null) {
				log.error("Could not find any matching i18n field container for node {" + getUuid() + "}.");
			} else {
				displayFieldName = getSchema().getDisplayField();
				return container.getString(displayFieldName).getString();
			}
		} catch (Exception e) {
			log.error("Could not determine displayName for node {" + getUuid() + "} and fieldName {" + displayFieldName + "}");
		}
		return null;
	}

	@Override
	public void setPublished(boolean published) {
		setProperty(PUBLISHED_PROPERTY_KEY, String.valueOf(published));
	}

	@Override
	public boolean isPublished() {
		String fieldValue = getProperty(PUBLISHED_PROPERTY_KEY);
		return Boolean.valueOf(fieldValue);
	}

	@Override
	public void update(InternalActionContext ac, Handler<AsyncResult<Void>> handler) {
		Database db = MeshSpringConfiguration.getInstance().database();
		SearchQueueBatch batch;
		try {
			NodeUpdateRequest requestModel = JsonUtil.readNode(ac.getBodyAsString(), NodeUpdateRequest.class, ServerSchemaStorage.getSchemaStorage());
			if (StringUtils.isEmpty(requestModel.getLanguage())) {
				handler.handle(ac.failedFuture(BAD_REQUEST, "error_language_not_set"));
				return;
			}
			try (Trx txUpdate = db.trx()) {
				Language language = BootstrapInitializer.getBoot().languageRoot().findByLanguageTag(requestModel.getLanguage());
				if (language == null) {
					handler.handle(ac.failedFuture(BAD_REQUEST, "error_language_not_found", requestModel.getLanguage()));
					return;
				}

				/* TODO handle other fields, etc. */
				setPublished(requestModel.isPublished());
				setEditor(ac.getUser());
				setLastEditedTimestamp(System.currentTimeMillis());
				NodeGraphFieldContainer container = getOrCreateGraphFieldContainer(language);
				try {
					Schema schema = getSchema();
					container.updateFieldsFromRest(ac, requestModel.getFields(), schema);
				} catch (MeshSchemaException e) {
					// TODO i18n
					handler.handle(ac.failedFuture(BAD_REQUEST, e.getMessage()));
					txUpdate.failure();
				}
				batch = addIndexBatch(UPDATE_ACTION);
				txUpdate.success();
			}
			processOrFail2(ac, batch, handler);
		} catch (IOException e1) {
			log.error(e1);
			handler.handle(ac.failedFuture(BAD_REQUEST, e1.getMessage(), e1));
		}
	}

	@Override
	public Node moveTo(InternalActionContext ac, Node targetNode, Handler<AsyncResult<Void>> handler) {
		Database db = MeshSpringConfiguration.getInstance().database();

		// TODO should we add a guard that terminates this loop when it runs to long?
		// Check whether the target node is part of the subtree of the source node.
		Node parent = targetNode.getParentNode();
		while (parent != null) {
			if (parent.getUuid().equals(getUuid())) {
				handler.handle(ac.failedFuture(BAD_REQUEST, "node_move_error_not_allowd_to_move_node_into_one_of_its_children"));
				return this;
			}
			parent = parent.getParentNode();
		}

		try {
			if (!targetNode.getSchema().isFolder()) {
				handler.handle(ac.failedFuture(BAD_REQUEST, "node_move_error_targetnode_is_no_folder"));
				return this;
			}
		} catch (Exception e) {
			log.error("Could not load schema for target node during move action", e);
			// TODO maybe add better i18n error
			handler.handle(ac.failedFuture(BAD_REQUEST, "error"));
			return this;
		}

		if (getUuid().equals(targetNode.getUuid())) {
			handler.handle(ac.failedFuture(BAD_REQUEST, "node_move_error_same_nodes"));
			return this;
		}

		// TODO check whether there is a node in the target node that has the same name. We do this to prevent issues for the webroot api
		SearchQueueBatch batch;
		try (Trx txMove = db.trx()) {
			setParentNode(targetNode);
			setEditor(ac.getUser());
			setLastEditedTimestamp(System.currentTimeMillis());
			targetNode.setEditor(ac.getUser());
			targetNode.setLastEditedTimestamp(System.currentTimeMillis());
			batch = addIndexBatch(SearchQueueEntryAction.UPDATE_ACTION);
			txMove.success();
		}
		processOrFail2(ac, batch, handler);
		return this;
	}

	@Override
	public void addRelatedEntries(SearchQueueBatch batch, SearchQueueEntryAction action) {
		// batch.addEntry(getParentNode(), UPDATE_ACTION);
	}

	@Override
	public SearchQueueBatch addIndexBatch(SearchQueueEntryAction action) {
		SearchQueue queue = BootstrapInitializer.getBoot().meshRoot().getSearchQueue();
		SearchQueueBatch batch = queue.createBatch(UUIDUtil.randomUUID());
		for (NodeGraphFieldContainer container : getGraphFieldContainers()) {
			String indexType = getType() + "-" + container.getLanguage().getLanguageTag();
			batch.addEntry(getUuid(), getType(), action, indexType);
		}
		addRelatedEntries(batch, action);
		return batch;
	}
}
