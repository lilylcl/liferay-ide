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

package com.liferay.ide.functional.module.tests;

import com.liferay.ide.functional.liferay.SwtbotBase;
import com.liferay.ide.functional.liferay.support.project.ProjectSupport;
import com.liferay.ide.functional.liferay.support.server.PureTomcat71Support;
import com.liferay.ide.functional.liferay.util.RuleUtil;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * @author Rui Wang
 */
public class WatchModuleGradleTomcatTests extends SwtbotBase {

	public static PureTomcat71Support tomcat = new PureTomcat71Support(bot);

	@ClassRule
	public static RuleChain chain = RuleUtil.getTomcat7xRunningRuleChain(bot, tomcat);

	@Test
	public void watchJavaClassChanges() {
		wizardAction.openNewLiferayModuleWizard();

		wizardAction.newModule.prepareGradle(project.getName(), ACTIVATOR, "7.1");

		wizardAction.finish();

		jobAction.waitForNoRunningJobs();

		viewAction.project.runWatch(project.getName());

		jobAction.waitForConsoleContent(
			tomcat.getServerName() + " [Liferay 7.x]", "STARTED " + project.getName() + "_", M1);

		viewAction.console.clearConsole();

		viewAction.project.openFile(
			project.getName() + " [watching]", "src/main/java", project.getName(),
			project.getCapitalName() + "Activator.java");

		String text = "/*test*/";

		editorAction.customizedText(project.getCapitalName() + "Activator.java", 10, 1, text);

		editorAction.save();

		editorAction.close();

		jobAction.waitForConsoleContent(
			tomcat.getServerName() + " [Liferay 7.x]", "STARTED " + project.getName() + "_", M1);

		ide.showProgressView();

		viewAction.progress.stopWatch();

		viewAction.project.closeAndDeleteFromDisk(project.getName());
	}

	@Test
	public void watchMVCPortlet() {
		wizardAction.openNewLiferayModuleWizard();

		wizardAction.newModule.prepareGradle(project.getName(), MVC_PORTLET, "7.1");

		wizardAction.finish();

		jobAction.waitForNoRunningJobs();

		viewAction.project.runWatch(project.getName());

		jobAction.waitForConsoleContent(
			tomcat.getServerName() + " [Liferay 7.x]", "STARTED " + project.getName() + "_", M1);

		Assert.assertTrue(viewAction.project.visibleFileTry(project.getName() + " [watching]"));

		wizardAction.openNewLiferayWorkspaceWizard();

		wizardAction.newLiferayWorkspace.prepareGradle(project.getName());

		wizardAction.cancel();

		ide.showProgressView();

		viewAction.progress.stopWatch();

		viewAction.project.closeAndDeleteFromDisk(project.getName());
	}

	@Test
	public void watchServiceBuilder() {
		wizardAction.openNewLiferayModuleWizard();

		wizardAction.newModule.prepareGradle(project.getName(), SERVICE_BUILDER, "7.1");

		wizardAction.finish();

		jobAction.waitForNoRunningJobs();

		viewAction.project.runBuildService(project.getName());

		viewAction.project.runWatch(project.getName());

		jobAction.waitForConsoleContent(
			tomcat.getServerName() + " [Liferay 7.x]", "STARTED " + project.getName() + ".api_", M1);

		jobAction.waitForConsoleContent(
			tomcat.getServerName() + " [Liferay 7.x]", "STARTED " + project.getName() + ".service_", M1);

		ide.showProgressView();

		viewAction.progress.stopWatch();

		viewAction.project.closeAndDelete(project.getName(), project.getName() + "-api");
		viewAction.project.closeAndDelete(project.getName(), project.getName() + "-service");
		viewAction.project.closeAndDelete(project.getName());
	}

	@Rule
	public ProjectSupport project = new ProjectSupport(bot);

}