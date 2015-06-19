package com.gentics.mesh.core.verticle;

import static com.gentics.mesh.core.data.model.relationship.Permission.CREATE_PERM;
import static com.gentics.mesh.core.data.model.relationship.Permission.DELETE_PERM;
import static com.gentics.mesh.core.data.model.relationship.Permission.READ_PERM;
import static com.gentics.mesh.core.data.model.relationship.Permission.UPDATE_PERM;
import static com.gentics.mesh.util.JsonUtils.fromJson;
import static com.gentics.mesh.util.JsonUtils.toJson;
import static com.gentics.mesh.util.RoutingContextHelper.getUser;
import static io.vertx.core.http.HttpMethod.DELETE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.ext.web.Route;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jacpfx.vertx.spring.SpringVerticle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gentics.mesh.core.AbstractProjectRestVerticle;
import com.gentics.mesh.core.Page;
import com.gentics.mesh.core.data.model.tinkerpop.I18NProperties;
import com.gentics.mesh.core.data.model.tinkerpop.Language;
import com.gentics.mesh.core.data.model.tinkerpop.MeshShiroUser;
import com.gentics.mesh.core.data.model.tinkerpop.Project;
import com.gentics.mesh.core.data.model.tinkerpop.Tag;
import com.gentics.mesh.core.rest.common.response.GenericMessageResponse;
import com.gentics.mesh.core.rest.tag.request.TagCreateRequest;
import com.gentics.mesh.core.rest.tag.request.TagUpdateRequest;
import com.gentics.mesh.core.rest.tag.response.TagListResponse;
import com.gentics.mesh.core.verticle.handler.MeshNodeListHandler;
import com.gentics.mesh.core.verticle.handler.TagListHandler;
import com.gentics.mesh.paging.PagingInfo;
import com.gentics.mesh.util.RestModelPagingHelper;

/**
 * The tag verticle provides rest endpoints which allow manipulation and handling of tag related objects.
 * 
 * @author johannes2
 *
 */
@Component
@Scope("singleton")
@SpringVerticle
public class TagVerticle extends AbstractProjectRestVerticle {

	private static final Logger log = LoggerFactory.getLogger(TagVerticle.class);

	@Autowired
	private TagListHandler tagListHandler;

	@Autowired
	private MeshNodeListHandler nodeListHandler;

	public TagVerticle() {
		super("tags");
	}

	@Override
	public void registerEndPoints() throws Exception {
		route("/*").handler(springConfiguration.authHandler());
		addCreateHandler();
		addReadHandler();
		addUpdateHandler();
		addDeleteHandler();

		addTaggedNodesHandler();
	}

	private void addTaggedNodesHandler() {
		Route getRoute = route("/:uuid/nodes").method(GET).produces(APPLICATION_JSON);
		getRoute.handler(rc -> {
			MeshShiroUser requestUser = getUser(rc);

			nodeListHandler.handleListByTag(rc, (projectName, tag, languageTags, pagingInfo) -> {
				return tag.findTaggedNodes(requestUser, projectName, languageTags, pagingInfo);
			});
		});
	}

	// TODO fetch project specific tag
	// TODO update other fields as well?
	// TODO Update user information
	// TODO use schema and only handle those i18n properties that were specified within the schema.
	private void addUpdateHandler() {
		Route route = route("/:uuid").method(PUT).consumes(APPLICATION_JSON).produces(APPLICATION_JSON);
		route.handler(rc -> {
			String projectName = rcs.getProjectName(rc);
			MeshShiroUser requestUser = getUser(rc);

			List<String> languageTags = rcs.getSelectedLanguageTags(rc);
			rcs.loadObject(rc, "uuid", projectName, UPDATE_PERM, (AsyncResult<Tag> rh) -> {
				Tag tag = rh.result();

				TagUpdateRequest requestModel = fromJson(rc, TagUpdateRequest.class);

				// Iterate through all properties and update the changed ones
					String requestLanguage = requestModel.getLanguage();
					Language language = languageService.findByLanguageTag(requestLanguage);
					if (language != null) {
						Map<String, String> properties = requestModel.getProperties();

						if (properties != null) {
							I18NProperties i18nProperties = tag.getI18nProperties(language);

							for (Map.Entry<String, String> set : properties.entrySet()) {
								String key = set.getKey();
								String value = set.getValue();
								String i18nValue = i18nProperties.getProperty(key);
								if (i18nValue == null) {
									i18nProperties.setProperty(key, value);
								} else {
									if (!value.equals(i18nValue)) {
										i18nProperties.setProperty(key, value);
									}
								}
							}

							/*
							 * Check whether there are any key missing in the request. This would mean we should remove those i18n properties. First lets
							 * collect those keys
							 */
							Set<String> keysToBeRemoved = new HashSet<>();
							for (String i18nKey : i18nProperties.getProperties().keySet()) {
								if (!properties.containsKey(i18nKey)) {
									keysToBeRemoved.add(i18nKey);
								}
							}

							/* Now remove the keys */
							for (String key : keysToBeRemoved) {
								i18nProperties.removeProperty(key);
							}
						}

					}
					rc.response().setStatusCode(200).end(toJson(tag.transformToRest(requestUser)));
				});

		});

	}

	// TODO load project specific root tag
	// TODO handle creator
	// TODO load schema and set the reference to the tag
	// newTag.setSchemaName(request.getSchemaName());
	// TODO maybe projects should not be a set?
	private void addCreateHandler() {
		Route route = route("/").method(POST).consumes(APPLICATION_JSON).produces(APPLICATION_JSON);
		route.handler(rc -> {
			String projectName = rcs.getProjectName(rc);
			MeshShiroUser requestUser = getUser(rc);
			List<String> languageTags = rcs.getSelectedLanguageTags(rc);

			Future<Tag> tagCreated = Future.future();
			TagCreateRequest request = fromJson(rc, TagCreateRequest.class);
			rcs.loadObjectByUuid(rc, request.getTagUuid(), CREATE_PERM, (AsyncResult<Tag> rh) -> {
				Tag rootTag = rh.result();

				Tag newTag = tagService.create();

				Project project = projectService.findByName(projectName);
				newTag.addProject(project);
				String requestLanguage = request.getLanguage();

				Map<String, String> i18nProperties = request.getProperties();
				Language language = languageService.findByLanguageTag(requestLanguage);
				//TODO check whether language could be found?
					I18NProperties tagProps = newTag.getOrCreateI18nProperties(language);
					for (Map.Entry<String, String> entry : i18nProperties.entrySet()) {
						tagProps.setProperty(entry.getKey(), entry.getValue());
					}
					// Create the relationship to the i18n properties
					newTag.addI18nProperties(tagProps);
					tagCreated.complete(newTag);
				}, trh -> {
					Tag newTag = tagCreated.result();
					rc.response().setStatusCode(200).end(toJson(newTag.transformToRest(requestUser)));
				});

		});
	}

	// TODO filtering, sorting
	private void addReadHandler() {
		Route route = route("/:uuid").method(GET).produces(APPLICATION_JSON);
		route.handler(rc -> {
			MeshShiroUser requestUser = getUser(rc);

			rcs.loadObject(rc, "uuid", READ_PERM, null, (AsyncResult<Tag> trh) -> {
				Tag tag = trh.result();
				rc.response().setStatusCode(200).end(toJson(tag.transformToRest(requestUser)));
			});
		});

		Route readAllRoute = route().method(GET).produces(APPLICATION_JSON);
		readAllRoute.handler(rc -> {
			String projectName = rcs.getProjectName(rc);
			MeshShiroUser requestUser = getUser(rc);

			List<String> languageTags = rcs.getSelectedLanguageTags(rc);
			vertx.executeBlocking((Future<TagListResponse> bcr) -> {
				TagListResponse listResponse = new TagListResponse();
				PagingInfo pagingInfo = rcs.getPagingInfo(rc);
				Page<? extends Tag> tagPage;
				try {
					tagPage = tagService.findProjectTags(requestUser, projectName, languageTags, pagingInfo);
					for (Tag tag : tagPage) {
						listResponse.getData().add(tag.transformToRest(requestUser));
					}
					RestModelPagingHelper.setPaging(listResponse, tagPage, pagingInfo);
					bcr.complete(listResponse);
				} catch (Exception e) {
					bcr.fail(e);
				}
			}, arh -> {
				if (arh.failed()) {
					rc.fail(arh.cause());
				}
				TagListResponse listResponse = arh.result();
				rc.response().setStatusCode(200).end(toJson(listResponse));
			});

		});

	}

	// TODO filter by projectName
	private void addDeleteHandler() {
		Route route = route("/:uuid").method(DELETE).produces(APPLICATION_JSON);
		route.handler(rc -> {
			String projectName = rcs.getProjectName(rc);
			rcs.loadObject(rc, "uuid", projectName, DELETE_PERM, (AsyncResult<Tag> rh) -> {
				Tag tag = rh.result();
				tag.delete();
			}, trh -> {
				String uuid = rc.request().params().get("uuid");
				rc.response().setStatusCode(200).end(toJson(new GenericMessageResponse(i18n.get(rc, "tag_deleted", uuid))));
			});
		});
	}

}
