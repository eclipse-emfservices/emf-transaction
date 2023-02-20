/**
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors: 
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.workspace.internal;


/**
 * Error status codes for the EMF Workbench plug-in.
 * 
 * @author Christian W. Damus (cdamus)
 */
public class EMFWorkspaceStatusCodes {

	public static final int EXECUTE_INTERRUPTED = 20;
	public static final int EXECUTE_ROLLED_BACK = 21;
	public static final int UNDO_INTERRUPTED = 22;
	public static final int UNDO_ROLLED_BACK = 23;
	public static final int REDO_INTERRUPTED = 24;
	public static final int REDO_ROLLED_BACK = 25;
	
	public static final int UNDO_RECOVERY_FAILED = 30;
	public static final int REDO_RECOVERY_FAILED = 31;
	public static final int ROLLBACK_FAILED = 32;
	
	public static final int PRECOMMIT_FAILED = 43;
	
	public static final int EXCEPTION_HANDLER_FAILED = 50;
	
	/** Not instantiable. */
	private EMFWorkspaceStatusCodes() {
		super();
	}
}
