package com.gentics.mesh.core.data.root;

import com.gentics.mesh.core.data.Release;
import com.gentics.mesh.core.data.User;
import com.gentics.mesh.core.data.schema.MicroschemaContainer;
import com.gentics.mesh.core.data.schema.MicroschemaContainerVersion;
import com.gentics.mesh.core.rest.schema.Microschema;
import com.gentics.mesh.core.rest.schema.MicroschemaReference;

public interface MicroschemaContainerRoot extends RootVertex<MicroschemaContainer> {

	public static final String TYPE = "microschemas";

	/**
	 * Add the microschema container to the aggregation node.
	 * 
	 * @param container
	 */
	void addMicroschema(MicroschemaContainer container);

	/**
	 * Remove the microschema container from the aggregation node.
	 * 
	 * @param container
	 */
	void removeMicroschema(MicroschemaContainer container);

	/**
	 * Create a new microschema container.
	 * 
	 * @param microschema
	 * @param user
	 *            User that is used to set creator and editor references.
	 * @return
	 */
	MicroschemaContainer create(Microschema microschema, User user);

	/**
	 * Check whether the given microschema is assigned to this root node.
	 * 
	 * @param microschema
	 * @return
	 */
	boolean contains(MicroschemaContainer microschema);

	/**
	 * Get the microschema container version from the given reference.
	 * 
	 * @param reference
	 *            reference
	 * @return
	 */
	MicroschemaContainerVersion fromReference(MicroschemaReference reference);

	/**
	 * Get the microschema container version from the given reference. Ignore the version number from the reference, but take the version from the release
	 * instead.
	 * 
	 * @param reference
	 *            reference
	 * @param release
	 *            release
	 * @return
	 */
	MicroschemaContainerVersion fromReference(MicroschemaReference reference, Release release);
}