/**
 * Copyright (c) 2017--2021, Stephan Saalfeld
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.janelia.saalfeldlab.n5.zarr.v3;

import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.CompressionAdapter;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.KeyValueAccess;
import org.janelia.saalfeldlab.n5.N5Exception;
import org.janelia.saalfeldlab.n5.N5KeyValueReader;
import org.janelia.saalfeldlab.n5.zarr.Filter;
import org.janelia.saalfeldlab.n5.zarr.ZarrCompressor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.janelia.saalfeldlab.n5.zarr.chunks.ChunkAttributes;
import org.janelia.saalfeldlab.n5.zarr.chunks.ChunkGrid;
import org.janelia.saalfeldlab.n5.zarr.chunks.ChunkKeyEncoding;
import org.janelia.saalfeldlab.n5.zarr.serialization.ChunkGridAdapter;
import org.janelia.saalfeldlab.n5.zarr.serialization.ChunkKeyEncodingAdapter;

public class ZarrV3KeyValueReader extends N5KeyValueReader {

	// Override this constant
	// if we try supporting v2 and v3 in parallel
	public static final Version VERSION = new Version(3, 0, 0);

	public static final String ZARR_KEY = "zarr.json";

	final protected boolean mapN5DatasetAttributes;

	final protected boolean mergeAttributes;

	/**
	 * Opens an {@link ZarrV3KeyValueReader} at a given base path with a custom
	 * {@link GsonBuilder} to support custom attributes.
	 *
	 * @param checkVersion
	 *            perform version check
	 * @param keyValueAccess
	 * @param basePath
	 *            N5 base path
	 * @param gsonBuilder
	 *            the gson builder
	 * @param mapN5DatasetAttributes
	 *            If true, getAttributes and variants of getAttribute methods
	 *            will
	 *            contain keys used by n5 datasets, and whose values are those
	 *            for
	 *            their corresponding zarr fields. For example, if true, the key
	 *            "dimensions"
	 *            (from n5) may be used to obtain the value of the key "shape"
	 *            (from zarr).
	 * @param mergeAttributes
	 *            If true, fields from .zgroup, .zarray, and .zattrs will be
	 *            merged
	 *            when calling getAttributes, and variants of getAttribute
	 * @param cacheMeta
	 *            cache attributes and meta data
	 *            Setting this to true avoids frequent reading and parsing of
	 *            JSON
	 *            encoded attributes and other meta data that requires accessing
	 *            the
	 *            store. This is most interesting for high latency backends.
	 *            Changes
	 *            of cached attributes and meta data by an independent writer
	 *            will
	 *            not be tracked.
	 *
	 * @throws N5Exception
	 *             if the base path cannot be read or does not exist,
	 *             if the N5 version of the container is not compatible with
	 *             this
	 *             implementation.
	 */
	public ZarrV3KeyValueReader(
			final boolean checkVersion,
			final KeyValueAccess keyValueAccess,
			final String basePath,
			final GsonBuilder gsonBuilder,
			final boolean mapN5DatasetAttributes,
			final boolean mergeAttributes,
			final boolean cacheMeta)
			throws N5Exception {

		this(checkVersion, keyValueAccess, basePath, gsonBuilder, mapN5DatasetAttributes, mergeAttributes, cacheMeta, true);
	}

	/**
	 * Opens an {@link ZarrV3KeyValueReader} at a given base path with a custom
	 * {@link GsonBuilder} to support custom attributes.
	 *
	 * @param keyValueAccess
	 * @param basePath
	 *            N5 base path
	 * @param gsonBuilder
	 * @param cacheMeta
	 *            cache attributes and meta data
	 *            Setting this to true avoids frequent reading and parsing of
	 *            JSON
	 *            encoded attributes and other meta data that requires accessing
	 *            the
	 *            store. This is most interesting for high latency backends.
	 *            Changes
	 *            of cached attributes and meta data by an independent writer
	 *            will
	 *            not be tracked.
	 *
	 * @throws N5Exception
	 *             if the base path cannot be read or does not exist,
	 *             if the N5 version of the container is not compatible with
	 *             this
	 *             implementation.
	 */
	public ZarrV3KeyValueReader(
			final KeyValueAccess keyValueAccess,
			final String basePath,
			final GsonBuilder gsonBuilder,
			final boolean mapN5DatasetAttributes,
			final boolean mergeAttributes,
			final boolean cacheMeta)
			throws N5Exception {

		this(true, keyValueAccess, basePath, gsonBuilder, mapN5DatasetAttributes, mergeAttributes, cacheMeta);
	}

	protected ZarrV3KeyValueReader(
			final boolean checkVersion,
			final KeyValueAccess keyValueAccess,
			final String basePath,
			final GsonBuilder gsonBuilder,
			final boolean mapN5DatasetAttributes,
			final boolean mergeAttributes,
			final boolean cacheMeta,
			final boolean checkRootExists) {

		super(checkVersion, keyValueAccess, basePath, addTypeAdapters(gsonBuilder), cacheMeta, checkRootExists);
		this.mergeAttributes = mergeAttributes;
		this.mapN5DatasetAttributes = mapN5DatasetAttributes;
	}

	@Override
	public String getAttributesKey() {

		return ZARR_KEY;
	}

	@Override
	public String toString() {

		return String.format("%s[access=%s, basePath=%s]", getClass().getSimpleName(), keyValueAccess, uri.getPath());
	}

	static Gson registerGson(final GsonBuilder gsonBuilder) {

		return addTypeAdapters(gsonBuilder).create();
	}

	protected static GsonBuilder addTypeAdapters(final GsonBuilder gsonBuilder) {

		gsonBuilder.registerTypeAdapter(DataType.class, new DataType.JsonAdapter());
		gsonBuilder.registerTypeAdapter(ZarrCompressor.class, ZarrCompressor.jsonAdapter);
		gsonBuilder.registerTypeAdapter(ZarrCompressor.Raw.class, ZarrCompressor.rawNullAdapter);
		gsonBuilder.registerTypeHierarchyAdapter(Compression.class, CompressionAdapter.getJsonAdapter());
		gsonBuilder.registerTypeAdapter(Compression.class, CompressionAdapter.getJsonAdapter());
		// gsonBuilder.registerTypeAdapter(ZArrayV3Attributes2.class, ZArrayV3Attributes2.jsonAdapter);
		gsonBuilder.registerTypeHierarchyAdapter(ChunkGrid.class, ChunkGridAdapter.getJsonAdapter());
		gsonBuilder.registerTypeHierarchyAdapter(ChunkKeyEncoding.class, ChunkKeyEncodingAdapter.getJsonAdapter());
		gsonBuilder.registerTypeHierarchyAdapter(ChunkAttributes.class, ChunkAttributes.getJsonAdapter());
		gsonBuilder.registerTypeHierarchyAdapter(Filter.class, Filter.jsonAdapter);
		gsonBuilder.disableHtmlEscaping();

		return gsonBuilder;
	}

}