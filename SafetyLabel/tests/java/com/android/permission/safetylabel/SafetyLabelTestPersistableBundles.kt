/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.permission.safetylabel

import android.os.PersistableBundle
import com.android.permission.safetylabel.DataCategoryConstants.CATEGORY_LOCATION
import com.android.permission.safetylabel.DataCategoryConstants.Category
import com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_ADVERTISING
import com.android.permission.safetylabel.DataPurposeConstants.PURPOSE_APP_FUNCTIONALITY
import com.android.permission.safetylabel.DataType.KEY_EPHEMERAL
import com.android.permission.safetylabel.DataType.KEY_PURPOSES
import com.android.permission.safetylabel.DataType.KEY_USER_CONTROL

/** A class that facilitates creating test safety label persistable bundles. */
object SafetyLabelTestPersistableBundles {
    const val INVALID_KEY = "invalid_key"

    fun createMetadataPersistableBundle(): PersistableBundle {
        return PersistableBundle().apply {
            putPersistableBundle(SafetyLabel.KEY_SAFETY_LABEL, createSafetyLabelPersistableBundle())
        }
    }

    fun createInvalidMetadataPersistableBundle(): PersistableBundle {
        return PersistableBundle().apply {
            putPersistableBundle(INVALID_KEY, createSafetyLabelPersistableBundle())
        }
    }

    fun createMetadataPersistableBundleWithInvalidSafetyLabel(): PersistableBundle {
        return PersistableBundle().apply {
            putPersistableBundle(
                SafetyLabel.KEY_SAFETY_LABEL, createInvalidSafetyLabelPersistableBundle())
        }
    }

    /** Returns [PersistableBundle] representation of a valid safety label */
    fun createSafetyLabelPersistableBundle(): PersistableBundle {
        return PersistableBundle().apply {
            putPersistableBundle(DataLabel.KEY_DATA_LABEL, createDataLabelPersistableBundle())
        }
    }

    /** Returns [PersistableBundle] representation of an ivnalid safety label */
    fun createInvalidSafetyLabelPersistableBundle(): PersistableBundle {
        return PersistableBundle().apply {
            putPersistableBundle(INVALID_KEY, createDataLabelPersistableBundle())
        }
    }

    /** Returns [PersistableBundle] representation of an ivnalid safety label */
    fun createSafetyLabelPersistableBundleWithNullDataCollected(): PersistableBundle {
        return PersistableBundle().apply {
            putPersistableBundle(
                DataLabel.KEY_DATA_LABEL, createDataLabelPersistableBundleWithNullDataCollected())
        }
    }

    /** Returns [PersistableBundle] representation of an ivnalid safety label */
    fun createSafetyLabelPersistableBundleWithNullDataShared(): PersistableBundle {
        return PersistableBundle().apply {
            putPersistableBundle(
                DataLabel.KEY_DATA_LABEL, createDataLabelPersistableBundleWithNullDataShared())
        }
    }

    /** Returns [PersistableBundle] representation of an ivnalid safety label */
    fun createSafetyLabelPersistableBundleWithEmptyDataCollected(): PersistableBundle {
        return PersistableBundle().apply {
            putPersistableBundle(
                DataLabel.KEY_DATA_LABEL, createDataLabelPersistableBundleWithEmptyDataCollected())
        }
    }

    /** Returns [PersistableBundle] representation of an ivnalid safety label */
    fun createSafetyLabelPersistableBundleWithEmptyDataShared(): PersistableBundle {
        return PersistableBundle().apply {
            putPersistableBundle(
                DataLabel.KEY_DATA_LABEL, createDataLabelPersistableBundleWithEmptyDataShared())
        }
    }

    /** Returns [PersistableBundle] representation of an ivnalid safety label */
    fun createSafetyLabelPersistableBundleWithInvalidDataCollected(): PersistableBundle {
        return PersistableBundle().apply {
            putPersistableBundle(
                DataLabel.KEY_DATA_LABEL,
                createDataLabelPersistableBundleWithInvalidDataCollected())
        }
    }

    /** Returns [PersistableBundle] representation of an ivnalid safety label */
    fun createSafetyLabelPersistableBundleWithInvalidDataShared(): PersistableBundle {
        return PersistableBundle().apply {
            putPersistableBundle(
                DataLabel.KEY_DATA_LABEL, createDataLabelPersistableBundleWithInvalidDataShared())
        }
    }

    fun createDataLabelPersistableBundle(): PersistableBundle {
        return PersistableBundle().apply {
            putPersistableBundle(
                DataLabelConstants.DATA_USAGE_SHARED, createCategoryMapPersistableBundle())
            putPersistableBundle(
                DataLabelConstants.DATA_USAGE_COLLECTED, createCategoryMapPersistableBundle())
        }
    }

    fun createInvalidDataLabelPersistableBundle(): PersistableBundle {
        return PersistableBundle().apply {
            putPersistableBundle(INVALID_KEY, createCategoryMapPersistableBundle())
        }
    }

    private fun createDataLabelPersistableBundleWithNullDataCollected(): PersistableBundle {
        val bundle = createDataLabelPersistableBundle()
        bundle.remove(DataLabelConstants.DATA_USAGE_COLLECTED)
        return bundle
    }

    private fun createDataLabelPersistableBundleWithNullDataShared(): PersistableBundle {
        val bundle = createDataLabelPersistableBundle()
        bundle.remove(DataLabelConstants.DATA_USAGE_SHARED)
        return bundle
    }

    private fun createDataLabelPersistableBundleWithEmptyDataCollected(): PersistableBundle {
        val bundle = createDataLabelPersistableBundle()
        bundle.remove(DataLabelConstants.DATA_USAGE_COLLECTED)
        bundle.putPersistableBundle(
            DataLabelConstants.DATA_USAGE_COLLECTED, PersistableBundle.EMPTY)
        return bundle
    }

    private fun createDataLabelPersistableBundleWithEmptyDataShared(): PersistableBundle {
        val bundle = createDataLabelPersistableBundle()
        bundle.remove(DataLabelConstants.DATA_USAGE_SHARED)
        bundle.putPersistableBundle(DataLabelConstants.DATA_USAGE_SHARED, PersistableBundle.EMPTY)
        return bundle
    }

    private fun createDataLabelPersistableBundleWithInvalidDataCollected(): PersistableBundle {
        val bundle = createDataLabelPersistableBundle()
        bundle.remove(DataLabelConstants.DATA_USAGE_COLLECTED)
        bundle.putPersistableBundle(
            DataLabelConstants.DATA_USAGE_COLLECTED, createInvalidCategoryMapPersistableBundle())
        return bundle
    }

    private fun createDataLabelPersistableBundleWithInvalidDataShared(): PersistableBundle {
        val bundle = createDataLabelPersistableBundle()
        bundle.remove(DataLabelConstants.DATA_USAGE_SHARED)
        bundle.putPersistableBundle(
            DataLabelConstants.DATA_USAGE_SHARED, createInvalidCategoryMapPersistableBundle())
        return bundle
    }

    fun createDataLabelPersistableBundleWithAdditonalInvalidCategory(): PersistableBundle {
        return PersistableBundle().apply {
            putPersistableBundle(
                DataLabelConstants.DATA_USAGE_SHARED,
                createCategoryMapPersistableBundleWithAdditionalInvalidCategory())
            putPersistableBundle(
                DataLabelConstants.DATA_USAGE_COLLECTED,
                createCategoryMapPersistableBundleWithAdditionalInvalidCategory())
        }
    }

    /** Returns [PersistableBundle] representation of a [Map] of valid data categories */
    fun createCategoryMapPersistableBundle(): PersistableBundle {
        return PersistableBundle().apply {
            DataCategoryConstants.VALID_CATEGORIES.forEach { categoryKey ->
                putPersistableBundle(categoryKey, createTypeMapPersistableBundle(categoryKey))
            }
        }
    }

    /** Returns [PersistableBundle] representation of a [Map] of valid data categories */
    fun createCategoryMapPersistableBundleWithAdditionalInvalidCategory(): PersistableBundle {
        return PersistableBundle().apply {
            DataCategoryConstants.VALID_CATEGORIES.forEach { categoryKey ->
                putPersistableBundle(categoryKey, createTypeMapPersistableBundle(categoryKey))
            }
            putPersistableBundle(INVALID_KEY, createTypeMapPersistableBundle(CATEGORY_LOCATION))
        }
    }

    /**
     * Returns [PersistableBundle] representation of a [Map] of valid data categories and invalid
     * types
     */
    fun createCategoryMapPersistableBundleWithInvalidTypes(): PersistableBundle {
        return PersistableBundle().apply {
            DataCategoryConstants.VALID_CATEGORIES.forEach { categoryKey ->
                putPersistableBundle(categoryKey, createInvalidTypeMapPersistableBundle())
            }
        }
    }

    /** Returns [PersistableBundle] representation of a [Map] of invalid data categories */
    fun createInvalidCategoryMapPersistableBundle(): PersistableBundle {
        return PersistableBundle().apply {
            putPersistableBundle(INVALID_KEY, createTypeMapPersistableBundle(CATEGORY_LOCATION))
        }
    }

    /** Returns [PersistableBundle] representation of a [Map] of valid data type */
    fun createTypeMapPersistableBundle(@Category category: String): PersistableBundle {
        return PersistableBundle().apply {
            DataTypeConstants.getValidDataTypesForCategory(category).forEach { type ->
                putPersistableBundle(type, createTypePersistableBundle())
            }
        }
    }

    /** Returns [PersistableBundle] representation of a [Map] of invalid data type */
    fun createInvalidTypeMapPersistableBundle(): PersistableBundle {
        return PersistableBundle().apply {
            putPersistableBundle(INVALID_KEY, createTypePersistableBundle())
        }
    }

    /** Returns [PersistableBundle] representation of a [Map] of valid type, with invalid data */
    fun createTypeMapWithInvalidTypeDataPersistableBundle(
        @Category category: String
    ): PersistableBundle {
        return PersistableBundle().apply {
            DataTypeConstants.getValidDataTypesForCategory(category).forEach { type ->
                putPersistableBundle(type, createInvalidTypePersistableBundle())
            }
        }
    }

    /** Returns [PersistableBundle] representation of a valid data type */
    fun createTypePersistableBundle(): PersistableBundle {
        return PersistableBundle().apply {
            putIntArray(KEY_PURPOSES, intArrayOf(PURPOSE_APP_FUNCTIONALITY, PURPOSE_ADVERTISING))
            putBoolean(KEY_USER_CONTROL, true)
            putBoolean(KEY_EPHEMERAL, true)
        }
    }

    /** Returns [PersistableBundle] representation of an invalid data type */
    fun createInvalidTypePersistableBundle(): PersistableBundle {
        return PersistableBundle().apply { putLong(INVALID_KEY, 0) }
    }
}
