/*******************************************************************************
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
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

package com.liferay.ide.portlet.core.descriptor;

import com.liferay.ide.core.ILiferayConstants;
import com.liferay.ide.core.util.NodeUtil;
import com.liferay.ide.portlet.core.PortletCore;
import com.liferay.ide.project.core.BaseValidator;
import com.liferay.ide.project.core.LiferayProjectCore;
import com.liferay.ide.project.core.ValidationPreferences;
import com.liferay.ide.project.core.util.ProjectUtil;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.osgi.util.NLS;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.validation.ValidationResult;
import org.eclipse.wst.validation.ValidationState;
import org.eclipse.wst.validation.ValidatorMessage;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMNode;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Gregory Amerson
 * @author Cindy Li
 * @author Kuo Zhang
 */
@SuppressWarnings( "restriction" )
public class PortletDescriptorValidator extends BaseValidator
{

    public static final String FILTER_CLASS_ELEMENT = "filter-class"; //$NON-NLS-1$

    public static final String LISTENER_CLASS_ELEMENT = "listener-class"; //$NON-NLS-1$

    public static final String MARKER_TYPE = "com.liferay.ide.portlet.core.portletDescriptorMarker"; //$NON-NLS-1$

    public static final String MESSAGE_RESOURCE_BUNDLE_NOT_FOUND = Msgs.resourceBundleNotFound;

    public static final String PORTLET_CLASS_ELEMENT = "portlet-class"; //$NON-NLS-1$

    public static final String PORTLET_ELEMENT = "portlet";

    public static final String PREFERENCE_NODE_QUALIFIER = LiferayProjectCore.getDefault().getBundle().getSymbolicName();

    public static final String RESOURCE_BUNDLE_ELEMENT = "resource-bundle"; //$NON-NLS-1$

    public static final String SUPPORTED_LOCALE_ELEMENT = "supported-locale"; //$NON-NLS-1$

    public PortletDescriptorValidator()
    {
        super();
    }

    protected void checkResourceBundleElements(
        IDOMDocument document, IJavaProject javaProject, IScopeContext[] preferenceScopes,
        List<Map<String, Object>> problems )
    {
        final NodeList resourceBundles = document.getElementsByTagName( RESOURCE_BUNDLE_ELEMENT );

        for( int i = 0; i < resourceBundles.getLength(); i++ )
        {
            final Node item = resourceBundles.item( i );

            Map<String, Object> problem =
                checkClassResource(
                    javaProject, item, PREFERENCE_NODE_QUALIFIER, preferenceScopes,
                    ValidationPreferences.PORTLET_XML_RESOURCE_BUNDLE_NOT_FOUND, MESSAGE_RESOURCE_BUNDLE_NOT_FOUND,
                    true );

            if( problem != null )
            {
                problems.add( problem );
            }
        }
    }

    protected void checkResourceBundleAndSupportedLocaleEncoding(
        IDOMDocument document, IJavaProject javaProject, String preferenceNodeQualifier,
        IScopeContext[] preferenceScopes, String resouceBundleEncodingPreferenceKey,
        String supportedLocaleEncodingPreferenceKey, List<Map<String, Object>> problems )
    {
        final NodeList portlets = document.getElementsByTagName( PORTLET_ELEMENT );
        final NodeList allResourceBundles = document.getElementsByTagName( RESOURCE_BUNDLE_ELEMENT );
        final NodeList allSupportedLocales = document.getElementsByTagName( SUPPORTED_LOCALE_ELEMENT );

        if( allResourceBundles.getLength() == 0 )
        {
            return;
        }

        if( portlets != null && portlets.getLength() > 0 )
        {
            for( int i = 0; i < portlets.getLength(); i++ )
            {
                Node portlet = portlets.item( i );
                Node resourceBundle = null;
                Set<Node> supportedLocales = new HashSet<Node>();

                if( allResourceBundles.getLength() > 0 )
                {
                    for( int j = 0; j < allResourceBundles.getLength(); j++ )
                    {
                        if( allResourceBundles.item( j ).getParentNode().equals( portlet ) )
                        {
                            resourceBundle = allResourceBundles.item( j );
                        }
                    }

                    if( allSupportedLocales.getLength() > 0 )
                    {
                        for( int k = 0; k < allSupportedLocales.getLength(); k++ )
                        {
                            if( allSupportedLocales.item( k ).getParentNode().equals( portlet ) )
                            {
                                supportedLocales.add( allSupportedLocales.item( k ) );
                            }
                        }
                    }

                    try
                    {
                        final IWorkspaceRoot workspaceRoot = javaProject.getJavaModel().getWorkspace().getRoot();

                        final IClasspathEntry[] classpathEntrys = javaProject.getResolvedClasspath( true );

                        for( IClasspathEntry entry : classpathEntrys )
                        {
                            if( entry.getEntryKind() == IClasspathEntry.CPE_SOURCE )
                            {
                                String resourceBundleVal =  NodeUtil.getTextContent( resourceBundle );

                                IFile resourceBundleFile =
                                    workspaceRoot.getFile( entry.getPath().append(
                                        resourceBundleVal.replaceAll( "\\.", "/" ) + ".properties" ) );

                                if( resourceBundleFile.exists() &&
                                    ! ILiferayConstants.LIFERAY_LANGUAGE_PROPERTIES_FILE_ENCODING_CHARSET.equals( resourceBundleFile.getCharset() ) )
                                {
                                    String msg =
                                        MessageFormat.format(
                                            Msgs.resourceBundleEncodingNotDefault,
                                            new Object[] { resourceBundleFile.getName() } );

                                    problems.add( createMarkerValues(
                                        preferenceNodeQualifier, preferenceScopes, resouceBundleEncodingPreferenceKey,
                                        (IDOMNode) resourceBundle, msg ) );
                                }

                                if( supportedLocales.size() > 0 )
                                {
                                    for( Node supportedLocale : supportedLocales )
                                    {
                                        String supportedLocaleVal = NodeUtil.getTextContent( supportedLocale );

                                        IFile supportedLocaleFile =
                                            workspaceRoot.getFile( entry.getPath().append(
                                                resourceBundleVal.replaceAll( "\\.", "/" ) + "_" + supportedLocaleVal +
                                                    ".properties" ) );

                                        if( supportedLocaleFile.exists() &&
                                            ! supportedLocaleFile.getCharset().equals(
                                                ILiferayConstants.LIFERAY_LANGUAGE_PROPERTIES_FILE_ENCODING_CHARSET ) )
                                        {
                                            String msg =
                                                MessageFormat.format(
                                                    Msgs.supportedLocaleEncodingNotDefault,
                                                    new Object[] { supportedLocaleFile.getName() } );

                                            problems.add( createMarkerValues(
                                                preferenceNodeQualifier, preferenceScopes,
                                                supportedLocaleEncodingPreferenceKey, (IDOMNode) supportedLocale, msg ) );

                                        }
                                    }
                                }
                            }
                        }
                    }
                    catch( Exception e )
                    {
                        PortletCore.logError( e );
                    }
                }
            }
        }
    }

    @SuppressWarnings( "unchecked" )
    protected Map<String, Object>[] detectProblems(
        IJavaProject javaProject, IFile portletXml, IScopeContext[] preferenceScopes ) throws CoreException
    {
        final List<Map<String, Object>> problems = new ArrayList<Map<String, Object>>();

        IStructuredModel model = null;

        try
        {
            model = StructuredModelManager.getModelManager().getModelForRead( portletXml );

            if( model instanceof IDOMModel )
            {
                IDOMDocument document = ( (IDOMModel) model ).getDocument();

                checkAllClassElements(
                    getAllClasseElements( "PortletClassElements.properties" ), javaProject, portletXml, //$NON-NLS-1$
                    ValidationPreferences.PORTLET_XML_CLASS_NOT_FOUND,
                    ValidationPreferences.PORTLET_XML_INCORRECT_CLASS_HIERARCHY, preferenceScopes,
                    PREFERENCE_NODE_QUALIFIER, problems );

                checkResourceBundleElements( document, javaProject, preferenceScopes, problems );

                checkResourceBundleAndSupportedLocaleEncoding(
                    document, javaProject, PREFERENCE_NODE_QUALIFIER, preferenceScopes,
                    ValidationPreferences.PORTLET_XML_RESOURCE_BUNDLE_ENCODING_NOT_DEFAULT,
                    ValidationPreferences.PORTLET_XML_SUPPORTED_LOCALE_ENCODING_NOT_DEFAULT, problems );
            }

        }
        catch( IOException e )
        {
            PortletCore.logError( e );
        }
        finally
        {
            if( model != null )
            {
                model.releaseFromRead();
            }
        }

        Map<String, Object>[] retval = new Map[problems.size()];

        return (Map<String, Object>[]) problems.toArray( retval );
    }

    @SuppressWarnings( "deprecation" )
    @Override
    public ValidationResult validate( IResource resource, int kind, ValidationState state, IProgressMonitor monitor )
    {
        if( resource.getType() != IResource.FILE )
        {
            return null;
        }

        ValidationResult result = new ValidationResult();

        IFile portletXml = (IFile) resource;

        if( portletXml.isAccessible() && ProjectUtil.isPortletProject( resource.getProject() ) )
        {
            final IJavaProject javaProject = JavaCore.create( portletXml.getProject() );

            if( javaProject.exists() )
            {
                IScopeContext[] scopes = new IScopeContext[] { new InstanceScope(), new DefaultScope() };

                ProjectScope projectScope = new ProjectScope( portletXml.getProject() );

                boolean useProjectSettings =
                    projectScope.getNode( PREFERENCE_NODE_QUALIFIER ).getBoolean(
                        LiferayProjectCore.USE_PROJECT_SETTINGS, false );

                if( useProjectSettings )
                {
                    scopes = new IScopeContext[] { projectScope, new InstanceScope(), new DefaultScope() };
                }

                try
                {
                    Map<String, Object>[] problems = detectProblems( javaProject, portletXml, scopes );

                    for( int i = 0; i < problems.length; i++ )
                    {
                        ValidatorMessage message =
                            ValidatorMessage.create( problems[i].get( IMarker.MESSAGE ).toString(), resource );
                        message.setType( MARKER_TYPE );
                        message.setAttributes( problems[i] );
                        result.add( message );
                    }
                }
                catch( Exception e )
                {
                    PortletCore.logError( e );
                }
            }
        }

        return result;
    }

    private static class Msgs extends NLS
    {
        public static String resourceBundleNotFound;
        public static String resourceBundleEncodingNotDefault;
        public static String supportedLocaleEncodingNotDefault;

        static
        {
            initializeMessages( PortletDescriptorValidator.class.getName(), Msgs.class );
        }
    }
}
