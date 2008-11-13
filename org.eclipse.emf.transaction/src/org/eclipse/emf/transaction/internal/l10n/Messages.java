/**
 * <copyright> 
 *
 * Copyright (c) 2005, 2008 IBM Corporation, Zeligsoft Inc. and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 *   IBM - Initial API and implementation
 *   Zeligsoft - Bugs 145877, 250253
 *
 * </copyright>
 *
 * $Id: Messages.java,v 1.9 2008/11/13 01:16:55 cdamus Exp $
 */
package org.eclipse.emf.transaction.internal.l10n;

import org.eclipse.osgi.util.NLS;


/**
 * Localized messages for the EMF Transaction plug-in.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class Messages
	extends NLS {

	private static final String BUNDLE_NAME = "org.eclipse.emf.transaction.internal.l10n.Messages"; //$NON-NLS-1$

	public static String factoryInterface;
	public static String factoryInitialization;
	public static String listenerInterface;
	public static String listenerInitialization;
	public static String removeStaticDomain;

	public static String validationFailure;
	public static String modifyReadOnlyResource;
	
	public static String noWriteTx;
	public static String concurrentWrite;
	public static String readTxRollback;
	public static String precommitInterrupted;
	public static String precommitFailed;
	public static String postcommitInterrupted;
	public static String postcommitFailed;
	
	public static String exceptionHandlerFailed;
	
	public static String acquireJobLabel;
	public static String upgradeReadLock;

	public static String privilegedRunnable;
	
	public static String reloadDuringUnload;
	
	public static String lifecycleListener;
	
	public static String rollbackRequested;
	
	static {
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

}
