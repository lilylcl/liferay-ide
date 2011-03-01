/*******************************************************************************
 * Copyright (c) 2000-2011 Liferay, Inc. All rights reserved.
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
 *
 *******************************************************************************/

package com.liferay.ide.eclipse.portlet.ui.action;

import com.liferay.ide.eclipse.core.ILiferayConstants;
import com.liferay.ide.eclipse.portlet.core.PortletCore;
import com.liferay.ide.eclipse.portlet.core.job.BuildWSDDJob;
import com.liferay.ide.eclipse.project.core.util.ProjectUtil;
import com.liferay.ide.eclipse.project.ui.action.AbstractProjectAction;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.IStructuredSelection;

/**
 * @author Greg Amerson
 */
public class BuildWSDDAction extends AbstractProjectAction {

	public BuildWSDDAction() {
		super();
	}

	public void run(IAction action) {
		if (fSelection instanceof IStructuredSelection) {
			Object[] elems = ((IStructuredSelection) fSelection).toArray();

			IFile servicesFile = null;

			Object elem = elems[0];

			if (elem instanceof IFile) {
				servicesFile = (IFile) elem;

			}
			else if (elem instanceof IProject) {
				IProject project = (IProject) elem;

				IFolder docroot = ProjectUtil.getDocroot(project);

				if (docroot != null && docroot.exists()) {
					servicesFile = docroot.getFile("WEB-INF/" + ILiferayConstants.LIFERAY_SERVICE_BUILDER_XML_FILE);
				}
			}

			if (servicesFile != null && servicesFile.exists()) {
				BuildWSDDJob job = PortletCore.createBuildWSDDJob(servicesFile);

				job.schedule();
			}
		}

	}

}
