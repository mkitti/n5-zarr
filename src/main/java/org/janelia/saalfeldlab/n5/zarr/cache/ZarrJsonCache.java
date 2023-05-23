package org.janelia.saalfeldlab.n5.zarr.cache;

import org.janelia.saalfeldlab.n5.cache.N5JsonCache;
import org.janelia.saalfeldlab.n5.cache.N5JsonCacheableContainer;
import org.janelia.saalfeldlab.n5.zarr.ZarrKeyValueReader;

import com.google.gson.JsonElement;

public class ZarrJsonCache extends N5JsonCache {

	public ZarrJsonCache(N5JsonCacheableContainer container) {
		super(container);
	}

	@Override
	public void updateCacheInfo(final String normalPathKey, final String normalCacheKey, final JsonElement uncachedAttributes) {

		N5CacheInfo cacheInfo = getCacheInfo(normalPathKey);
		if (cacheInfo == null ){
			addNewCacheInfo(normalPathKey, normalCacheKey, uncachedAttributes );
			return;
		} else if (!container.existsFromContainer(normalPathKey, null)) {
			cacheInfo = emptyCacheInfo;
		} else {
			if( cacheInfo == emptyCacheInfo )
				cacheInfo = newCacheInfo();

			if (normalCacheKey != null) {
				final JsonElement attributesToCache = uncachedAttributes == null
						? container.getAttributesFromContainer(normalPathKey, normalCacheKey)
						: uncachedAttributes;

				updateCacheAttributes(cacheInfo, normalCacheKey, attributesToCache);

				// if this path is a group, it it not a dataset
				// if this path is a dataset, it it not a group
				if (normalCacheKey.equals(ZarrKeyValueReader.zgroupFile)) {
					if (container.isGroupFromAttributes(normalCacheKey, attributesToCache)) {
						updateCacheIsGroup(cacheInfo, true);
						updateCacheIsDataset(cacheInfo, false);
					}
				} else if (normalCacheKey.equals(ZarrKeyValueReader.zarrayFile)) {
					if (container.isDatasetFromAttributes(normalCacheKey, attributesToCache)) {
						updateCacheIsGroup(cacheInfo, false);
						updateCacheIsDataset(cacheInfo, true);
					}
				}
			}
			else {
				updateCacheIsGroup(cacheInfo, container.isGroupFromContainer(normalPathKey));
				updateCacheIsGroup(cacheInfo, container.isDatasetFromContainer(normalPathKey));
			}
		}

		updateCache(normalPathKey, cacheInfo);
	}

}