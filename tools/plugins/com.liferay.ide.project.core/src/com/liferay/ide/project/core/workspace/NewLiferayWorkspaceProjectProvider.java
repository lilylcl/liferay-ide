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

import com.liferay.ide.project.core.NewLiferayProjectProvider;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.sapphire.ExecutableElement;

/**
 * @author Andy Wu
 */
public interface NewLiferayWorkspaceProjectProvider<T extends ExecutableElement> extends NewLiferayProjectProvider<T> {

	public String getInitBundleUrl(String workspaceLocation);

	public IStatus importProject(String location, IProgressMonitor monitor, boolean initBundle, String bundleUrl);

}