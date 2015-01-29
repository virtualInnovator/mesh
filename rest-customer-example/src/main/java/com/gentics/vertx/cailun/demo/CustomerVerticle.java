package com.gentics.vertx.cailun.demo;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;

import java.util.Arrays;

import org.jacpfx.vertx.spring.SpringVerticle;
import org.neo4j.graphdb.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gentics.vertx.cailun.auth.CaiLunConfiguration;
import com.gentics.vertx.cailun.auth.Neo4jSpringConfiguration;
import com.gentics.vertx.cailun.page.PageRepository;
import com.gentics.vertx.cailun.page.model.Page;
import com.gentics.vertx.cailun.perm.GroupRepository;
import com.gentics.vertx.cailun.perm.RoleRepository;
import com.gentics.vertx.cailun.perm.UserRepository;
import com.gentics.vertx.cailun.perm.model.Group;
import com.gentics.vertx.cailun.perm.model.PermissionSet;
import com.gentics.vertx.cailun.perm.model.Role;
import com.gentics.vertx.cailun.perm.model.User;
import com.gentics.vertx.cailun.rest.AbstractCailunRestVerticle;
import com.gentics.vertx.cailun.tag.TagRepository;
import com.gentics.vertx.cailun.tag.model.Tag;

/**
 * Dummy verticle that is used to setup basic demo data
 * 
 * @author johannes2
 *
 */
@Component
@Scope("singleton")
@SpringVerticle
public class CustomerVerticle extends AbstractCailunRestVerticle {

	private static Logger log = LoggerFactory.getLogger(CustomerVerticle.class);

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PageRepository pageRepository;

	@Autowired
	private TagRepository tagRepository;

	@Autowired
	private GroupRepository groupRepository;
	
	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private CaiLunConfiguration cailunConfig;
	
	@Autowired 
	private Neo4jSpringConfiguration neo4jSpringConfiguration;

	public CustomerVerticle() {
		super("page");
	}

	@Override
	public void start() throws Exception {
		super.start();

		// Users
		User john = new User("joe1");
		john.setFirstname("John");
		john.setLastname("Doe");
		john.setEmailAddress("j.doe@gentics.com");
		john.setPasswordHash(cailunConfig.passwordEncoder().encode("test123"));

		User mary = new User("mary2");
		mary.setFirstname("Mary");
		mary.setLastname("Doe");
		mary.setEmailAddress("m.doe@gentics.com");
		mary.setPasswordHash(cailunConfig.passwordEncoder().encode("lalala"));
		userRepository.save(Arrays.asList(john, mary));

		// Roles
		Role adminRole = new Role("admin role");
		roleRepository.save(adminRole);
		Role guestRole = new Role("guest role");
		roleRepository.save(guestRole);

		// Groups
		Group rootGroup = new Group("superusers");
		rootGroup.getMembers().add(john);
		rootGroup.getRoles().add(adminRole);

		groupRepository.save(rootGroup);
		Group guests = new Group("guests");
		guests.getParents().add(rootGroup);
		guests.getMembers().add(mary);
		guests.getRoles().add(guestRole);
		groupRepository.save(guests);

		// Content
		Tag rootTag = new Tag("/");
		rootTag.tag("home").tag("jotschi");
		rootTag.tag("root");
		rootTag.tag("var").tag("www");
		Tag wwwTag = rootTag.tag("var").tag("www");
		wwwTag.tag("site");
		Tag postsTag = wwwTag.tag("posts");
		Tag blogsTag = wwwTag.tag("blogs");
		tagRepository.save(rootTag);

		Page rootPage = new Page("rootPage");
		rootPage.setContent("This is root");
		rootPage.setFilename("index.html");
		rootPage.setTeaser("Yo root");
		rootPage.tag(rootTag);
		pageRepository.save(rootPage);

		for (int i = 0; i < 6; i++) {
			Page page = new Page("Hallo Welt");
			page.setFilename("some" + i + ".html");
			page.setContent("some content");
			page.tag(blogsTag);
			pageRepository.save(page);

		}

		for (int i = 0; i < 3; i++) {
			Page page = new Page("Hallo Welt");
			page.setFilename("some_posts" + i + ".html");
			page.setContent("some content");
			page.tag(postsTag);
			pageRepository.save(page);

		}
		Page page = new Page("New BlogPost");
		page.tag(blogsTag);
		page.setFilename("blog.html");
		page.setContent("This is the blogpost content");
		page.setAuthor("Jotschi");
		page.setTeaser("Jo this page is the second blogpost");
		pageRepository.save(page);

		page = new Page("Hallo Cailun");
		page.setFilename("some2.html");
		page.setContent("some more content");
		page.tag(postsTag);
		pageRepository.save(page);

		Page indexPage = new Page("Index With Perm");
		indexPage.setFilename("index.html");
		indexPage.setContent("The index page<br/><a href=\"${Page(10)}\">Link</a>");
		indexPage.setTitle("Index Title");
		indexPage.setAuthor("Jotschi");
		indexPage.setTeaser("Yo guckste hier");
		indexPage.tag(wwwTag);

		indexPage.linkTo(page);
		pageRepository.save(indexPage);

		try (Transaction tx = neo4jSpringConfiguration.getGraphDatabaseService().beginTx()) {
			// Add admin permissions to all pages
			for (Page currentPage : pageRepository.findAll()) {
				PermissionSet permSet = currentPage.addPermission(adminRole);
				permSet.setCanCreate(true);
				permSet.setCanRead(true);
				permSet.setCanDelete(true);
				pageRepository.save(currentPage);
			}
		}

	}

}
