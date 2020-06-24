/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.ide.project.core.workspace;

import com.liferay.ide.core.util.SapphireContentAccessor;

import org.eclipse.sapphire.Value;

/**
 * @author Gregory Amerson
 */
public class ProductVersionDefaultValueService extends AbstractDefaultValueService implements SapphireContentAccessor {

	@Override
	protected Value<Object> getDefaultValueProperty() {
		NewLiferayWorkspaceOp op = context(NewLiferayWorkspaceOp.class);

		return op.property(NewLiferayWorkspaceOp.PROP_PRODUCT_VERSION);
	}

	@Override
	protected String initDefaultValue() {
		return null;
	}

}