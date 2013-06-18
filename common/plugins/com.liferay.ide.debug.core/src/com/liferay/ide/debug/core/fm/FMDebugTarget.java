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
package com.liferay.ide.debug.core.fm;

import com.liferay.ide.core.util.CoreUtil;
import com.liferay.ide.debug.core.ILRDebugConstants;
import com.liferay.ide.debug.core.LiferayDebugCore;

import freemarker.debug.Breakpoint;
import freemarker.debug.DebuggedEnvironment;
import freemarker.debug.Debugger;
import freemarker.debug.DebuggerClient;
import freemarker.debug.DebuggerListener;
import freemarker.debug.EnvironmentSuspendedEvent;

import java.net.Inet4Address;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.ILineBreakpoint;
import org.eclipse.debug.core.model.IMemoryBlock;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.osgi.util.NLS;


/**
 * @author Gregory Amerson
 */
public class FMDebugTarget extends FMDebugElement implements IDebugTarget, IDebugEventSetListener
{
    public static final String FM_TEMPLATE_SERVLET_CONTEXT = "_SERVLET_CONTEXT_"; //$NON-NLS-1$

    private Debugger debuggerClient;
    private EventDispatchJob eventDispatchJob;
    private IStackFrame[] fmStackFrames = new IStackFrame[0];
    private FMThread fmThread;
    private String host;
    private ILaunch launch;
    private String name;
    private IProcess process;
    private boolean suspended = false;
    private FMDebugTarget target;
    private boolean terminated = false;
    private IThread[] threads = new IThread[0];

    class EventDispatchJob extends Job implements DebuggerListener
    {
        private boolean setup;

        public EventDispatchJob()
        {
            super( "Freemarker Event Dispatch" );
            setSystem( true );
        }

        public void environmentSuspended( final EnvironmentSuspendedEvent event ) throws RemoteException
        {
            final int lineNumber = event.getLine();
            final IBreakpoint[] breakpoints = getBreakpoints();

            boolean suspended = false;

            for( IBreakpoint breakpoint : breakpoints )
            {
                if( supportsBreakpoint( breakpoint ) )
                {
                    if( breakpoint instanceof ILineBreakpoint )
                    {
                        ILineBreakpoint lineBreakpoint = (ILineBreakpoint) breakpoint;

                        try
                        {
                            final int bpLineNumber = lineBreakpoint.getLineNumber();
                            final String templateName =
                                breakpoint.getMarker().getAttribute( ILRDebugConstants.FM_TEMPLATE_NAME, "" );
                            final String remoteTemplateName =
                                event.getName().replaceAll( FM_TEMPLATE_SERVLET_CONTEXT, "" );

                            if( bpLineNumber == lineNumber && remoteTemplateName.equals( templateName ) )
                            {
                                fmThread.setEnvironment( event.getEnvironment() );
                                fmThread.setBreakpoints( new IBreakpoint[] { breakpoint } );

                                String frameName = templateName + " line: " + lineNumber;
                                fmStackFrames = new FMStackFrame[] { new FMStackFrame( fmThread, frameName ) };

                                suspended = true;
                                break;
                            }
                        }
                        catch( CoreException e )
                        {
                        }
                    }
                }
            }

            if( suspended )
            {
                suspended( DebugEvent.BREAKPOINT );
            }
            else
            {
                // lets not pause the remote environment if for some reason the breakpoints don't match.
                new Job( "resuming remote environment" )
                {
                    @Override
                    protected IStatus run( IProgressMonitor monitor )
                    {
                        IStatus retval = Status.OK_STATUS;

                        try
                        {
                            event.getEnvironment().resume();
                        }
                        catch( RemoteException e )
                        {
                            retval = LiferayDebugCore.createErrorStatus( "Could not resume after missing breakpoint", e );
                        }

                        return retval;
                    }
                }.schedule();
            }
        }

        @Override
        protected IStatus run( IProgressMonitor monitor )
        {
            while( ! isTerminated() )
            {
                // try to connect to debugger
                Debugger debugger = getDebuggerClient();

                if( debugger == null )
                {
                    try
                    {
                        Thread.sleep( 1000 );
                    }
                    catch( InterruptedException e )
                    {
                    }

                    continue;
                }

                if( !setup )
                {
                    setup = setupDebugger(debugger);
                }

                synchronized( eventDispatchJob )
                {
                    try
                    {
                        System.out.println("EVENT DISPATCH JOB WAIT()");
                        wait();
                    }
                    catch( InterruptedException e )
                    {
                    }
                }
            }

            return Status.OK_STATUS;
        }

        private boolean setupDebugger( Debugger debugger )
        {
            try
            {
                debugger.addDebuggerListener( eventDispatchJob );

                FMDebugTarget.this.threads = new IThread[] { FMDebugTarget.this.fmThread };

                final IBreakpoint[] localBreakpoints =
                    getBreakpoints();

                addRemoteBreakpoints( debugger, localBreakpoints );
            }
            catch( RemoteException e )
            {
                return false;
            }

            return true;
        }
    }

    /**
     * Constructs a new debug target in the given launch for
     * the associated FM debugger
     * @param server
     *
     * @param launch containing launch
     * @param process Portal VM
     */
    public FMDebugTarget( String host, ILaunch launch, IProcess process )
    {
        super( null );

        this.target = this;
        this.host = host;
        this.launch = launch;
        this.process = process;

        this.fmThread = new FMThread( this.target );
        this.eventDispatchJob = new EventDispatchJob();
        this.eventDispatchJob.schedule();

        DebugPlugin.getDefault().getBreakpointManager().addBreakpointListener( this );

        DebugPlugin.getDefault().addDebugEventListener( this );
    }

    public void addRemoteBreakpoints( final Debugger debugger, final IBreakpoint bps[] ) throws RemoteException
    {
        final List<Breakpoint> remoteBps = new ArrayList<Breakpoint>();

        for( IBreakpoint bp : bps )
        {
            final int line = bp.getMarker().getAttribute( IMarker.LINE_NUMBER, -1 );
            final String templateName = bp.getMarker().getAttribute( ILRDebugConstants.FM_TEMPLATE_NAME, null );
            final String remoteTemplateName = createRemoteTemplateName( templateName );

            if( ! CoreUtil.isNullOrEmpty( remoteTemplateName ) && line > -1 )
            {
                remoteBps.add( new Breakpoint( remoteTemplateName, line ) );
            }
        }

        final Job job = new Job( "add remote breakpoints" )
        {
            @Override
            protected IStatus run( IProgressMonitor monitor )
            {
                IStatus retval = null;

                for( Breakpoint bp : remoteBps )
                {
                    try
                    {
                        debugger.addBreakpoint( bp );
                    }
                    catch( RemoteException e )
                    {
                        retval =
                            LiferayDebugCore.createErrorStatus(
                                NLS.bind(
                                    "Could not add remote breakpoint: {0}:{1}",
                                    new Object[] { bp.getTemplateName(), bp.getLine() } ), e );
                    }
                }

                return retval == null ? Status.OK_STATUS : retval;
            }
        };

        job.schedule();
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.debug.core.IBreakpointListener#breakpointAdded(org.eclipse.debug.core.model.IBreakpoint)
     */
    public void breakpointAdded( IBreakpoint breakpoint )
    {
        if( supportsBreakpoint( breakpoint ) && ! this.launch.isTerminated() )
        {
            try
            {
                if( breakpoint.isEnabled() )
                {
                    addRemoteBreakpoints( getDebuggerClient(), new IBreakpoint[] { breakpoint } );
                }
            }
            catch( Exception e )
            {
                LiferayDebugCore.logError( "Error adding breakpoint.", e );
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.debug.core.IBreakpointListener#breakpointChanged(org.eclipse.debug.core.model.IBreakpoint,
     * org.eclipse.core.resources.IMarkerDelta)
     */
    public void breakpointChanged( IBreakpoint breakpoint, IMarkerDelta delta )
    {
        if( supportsBreakpoint( breakpoint ) && ! this.launch.isTerminated() )
        {
            try
            {
                if( breakpoint.isEnabled() )
                {
                    breakpointAdded( breakpoint );
                }
                else
                {
                    breakpointRemoved( breakpoint, null );
                }
            }
            catch( CoreException e )
            {
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.debug.core.IBreakpointListener#breakpointRemoved(org.eclipse.debug.core.model.IBreakpoint,
     * org.eclipse.core.resources.IMarkerDelta)
     */
    public void breakpointRemoved( IBreakpoint breakpoint, IMarkerDelta delta )
    {
        if( supportsBreakpoint( breakpoint ) && ! this.launch.isTerminated() )
        {
            removeRemoteBreakpoints( new IBreakpoint[] { breakpoint } );
        }
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.debug.core.model.IDisconnect#canDisconnect()
     */
    public boolean canDisconnect()
    {
        return false;
    }

    public boolean canResume()
    {
        return !isTerminated() && isSuspended();
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.debug.core.model.ISuspendResume#canSuspend()
     */
    public boolean canSuspend()
    {
        return false;
    }

    public boolean canTerminate()
    {
        return getProcess().canTerminate();
    }

    private void cleanup()
    {
        this.terminated = true;
        this.suspended = false;

        DebugPlugin.getDefault().getBreakpointManager().removeBreakpointListener(this);
        DebugPlugin.getDefault().removeDebugEventListener( this );
    }

    private String createRemoteTemplateName( String templateName )
    {
        String retval = null;

        if( ! CoreUtil.isNullOrEmpty( templateName ) )
        {
            final IPath templatePath = new Path( templateName );
            final String firstSegment = templatePath.segment( 0 );
            final IProject project = CoreUtil.findProjectByContextName( firstSegment );

            if( project != null )
            {
                /*
                 * need to add special uri to the end of first segment of template path in order to make it work with
                 * remote debugger
                 */
                final Object[] bindings = new Object[] { firstSegment, FM_TEMPLATE_SERVLET_CONTEXT,
                    templatePath.removeFirstSegments( 1 ) };

                retval = NLS.bind( "{0}{1}/{2}", bindings );
            }
            else
            {
                retval = templatePath.toPortableString();
            }
        }

        return retval;
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.debug.core.model.IDisconnect#disconnect()
     */
    public void disconnect() throws DebugException
    {
    }

    private IBreakpoint[] getBreakpoints()
    {
        return DebugPlugin.getDefault().getBreakpointManager().getBreakpoints( getModelIdentifier() );
    }

    public Debugger getDebuggerClient()
    {
        if( this.debuggerClient == null )
        {
            try
            {
                this.debuggerClient =
                    DebuggerClient.getDebugger(
                        Inet4Address.getByName( this.host ), ILRDebugConstants.FM_DEBUG_PORT,
                        ILRDebugConstants.FM_DEBUG_PASSWORD );
            }
            catch( Exception e )
            {
            }
        }

        return this.debuggerClient;
    }

    public FMDebugTarget getDebugTarget()
    {
        return this.target;
    }

    public ILaunch getLaunch()
    {
        return this.launch;
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.debug.core.model.IMemoryBlockRetrieval#getMemoryBlock(long, long)
     */
    public IMemoryBlock getMemoryBlock( long startAddress, long length ) throws DebugException
    {
        return null;
    }

    public String getName() throws DebugException
    {
        if( this.name == null )
        {
            this.name = "Freemarker Debugger";
        }

        return this.name;
    }

    public IProcess getProcess()
    {
        return this.process;
    }

    protected IStackFrame[] getStackFrames()
    {
        return this.fmStackFrames;
    }

    public IThread[] getThreads() throws DebugException
    {
        return this.threads;
    }

    public void handleDebugEvents( DebugEvent[] events )
    {
        for( DebugEvent event : events )
        {
            if( event.getKind() == DebugEvent.TERMINATE )
            {
                if( event.getSource() instanceof IProcess )
                {
                    IProcess process = (IProcess) event.getSource();

                    if( process.isTerminated() )
                    {
                        cleanup();
                    }
                }
            }
        }
    }

    public boolean hasThreads() throws DebugException
    {
        return this.threads != null && this.threads.length > 0;
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.debug.core.model.IDisconnect#isDisconnected()
     */
    public boolean isDisconnected()
    {
        return false;
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.debug.core.model.ISuspendResume#isSuspended()
     */
    public boolean isSuspended()
    {
        return this.suspended;
    }

    public boolean isTerminated()
    {
        return this.terminated || getProcess().isTerminated();
    }

    private void removeRemoteBreakpoints( final IBreakpoint[] breakpoints )
    {
        final List<Breakpoint> remoteBreakpoints = new ArrayList<Breakpoint>();

        for( IBreakpoint bp : breakpoints )
        {
            final String templateName = bp.getMarker().getAttribute( ILRDebugConstants.FM_TEMPLATE_NAME, "" );

            final String remoteTemplateName =createRemoteTemplateName( templateName );

            final Breakpoint remoteBp =
                new Breakpoint( remoteTemplateName, bp.getMarker().getAttribute( IMarker.LINE_NUMBER, -1 ) );

            remoteBreakpoints.add( remoteBp );
        }

        final Job job = new Job( "remove remote breakpoints" )
        {
            @Override
            protected IStatus run( IProgressMonitor monitor )
            {
                IStatus retval = null;

                for( Breakpoint bp : remoteBreakpoints )
                {
                    try
                    {
                        getDebuggerClient().removeBreakpoint( bp );
                    }
                    catch( Exception e )
                    {
                        retval =
                            LiferayDebugCore.createErrorStatus(
                                NLS.bind(
                                    "Unable to get debug client to remove breakpoint: {0}:{1}",
                                    new Object[] { bp.getTemplateName(), bp.getLine() } ), e );
                    }
                }

                return retval == null ? Status.OK_STATUS : retval;
            }
        };

        job.schedule();
    }

    @SuppressWarnings( "rawtypes" )
    public void resume()
    {
        final Job job = new Job("resume")
        {
            @Override
            protected IStatus run( IProgressMonitor monitor )
            {
                try
                {
                    for( Iterator i = getDebuggerClient().getSuspendedEnvironments().iterator(); i.hasNext(); )
                    {
                        DebuggedEnvironment debuged = (DebuggedEnvironment) i.next();

                        try
                        {
                            debuged.resume();
                        }
                        catch( Exception e )
                        {
                            LiferayDebugCore.logError( "Could not resume suspended environment", e );
                        }
                    }

                    fmStackFrames = null;

                    resumed( DebugEvent.CLIENT_REQUEST );
                }
                catch( RemoteException e )
                {
                    LiferayDebugCore.logError( "Could not fully resume suspended environments", e );
                }

                return null;
            }
        };

        job.schedule();
    }

    public void resume( FMThread thread ) throws DebugException
    {
        try
        {
            thread.getEnvironment().resume();

            this.fmStackFrames = null;

            resumed( DebugEvent.CLIENT_REQUEST );
        }
        catch( RemoteException e )
        {
            throw new DebugException( LiferayDebugCore.createErrorStatus( e ) );
        }
    }

    /**
     * Notification the target has resumed for the given reason
     *
     * @param detail
     *            reason for the resume
     */
    private void resumed( int detail )
    {
        this.suspended = false;
        this.fmStackFrames = new IStackFrame[0];
        this.fmThread.fireResumeEvent( detail );
        this.fireResumeEvent( detail );
    }

    protected void step( FMThread thread ) throws DebugException
    {
        //TODO step()
    }

    public boolean supportsBreakpoint( IBreakpoint breakpoint )
    {
        if( breakpoint.getModelIdentifier().equals( ILRDebugConstants.ID_FM_DEBUG_MODEL ) )
        {
            try
            {
                return breakpoint.getMarker().getType().equals( LiferayDebugCore.ID_FM_BREAKPOINT_TYPE );
            }
            catch( CoreException e )
            {
                e.printStackTrace();
            }
        }

        return false;
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.debug.core.model.IMemoryBlockRetrieval#supportsStorageRetrieval()
     */
    public boolean supportsStorageRetrieval()
    {
        return false;
    }

    public void suspend() throws DebugException
    {
    }

    /**
     * Notification the target has suspended for the given reason
     *
     * @param detail
     *            reason for the suspend
     */
    private void suspended( int detail )
    {
        this.suspended = true;
        this.fmThread.fireSuspendEvent( detail );
    }

    public void terminate() throws DebugException
    {
        final IBreakpoint[] localBreakpoints = getBreakpoints();

        removeRemoteBreakpoints( localBreakpoints );

        resume();

        terminated();
    }

    /**
     * Called when this debug target terminates.
     */
    private void terminated()
    {
        cleanup();

        fireTerminateEvent();
    }

}
