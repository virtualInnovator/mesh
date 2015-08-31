package com.gentics.mesh.core.data.search;

import java.util.List;

import com.gentics.mesh.core.data.GenericVertex;
import com.gentics.mesh.core.data.MeshVertex;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * A batch of search queue entries. Usually a batch groups those elements that need to be updated in order to sync the search index with the graph database
 * changes.
 */
public interface SearchQueueBatch extends MeshVertex {

	public static final String BATCH_ID_PROPERTY_KEY = "batch_id";

	void addEntry(String uuid, String type, SearchQueueEntryAction action);

	void addEntry(GenericVertex<?> vertex, SearchQueueEntryAction action);

	void addEntry(SearchQueueEntry entry);

	List<? extends SearchQueueEntry> getEntries();

	void setBatchId(String batchId);

	String getBatchId();

	void process(Handler<AsyncResult<Void>> handler);
}