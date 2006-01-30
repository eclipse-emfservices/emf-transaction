/**
 * <copyright>
 *
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *
 * </copyright>
 *
 * $Id: EXTLibraryEditingDomainFactory.java,v 1.1 2006/01/30 16:30:09 cdamus Exp $
 */
package org.eclipse.emf.workspace.examples.extlibrary.domain;

import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.transaction.TXCommandStack;
import org.eclipse.emf.transaction.TXEditingDomain;
import org.eclipse.emf.workspace.WorkbenchEditingDomainFactory;

/**
 * An editing domain factory registered on the extension point to create our
 * shared editing domain for EXTLibrary model editors.
 *
 * @author Christian W. Damus (cdamus)
 */
public class EXTLibraryEditingDomainFactory implements TXEditingDomain.Factory {

	public TXEditingDomain createEditingDomain() {
		// create an editing domain with a default resource set implementation
		//    and delegating command execution to the default (workbench)
		//    operation history
		TXEditingDomain result = WorkbenchEditingDomainFactory.INSTANCE.createEditingDomain();
		
		// add an exception handler to the editing domain's command stack
		((TXCommandStack) result.getCommandStack()).setExceptionHandler(
				new CommandStackExceptionHandler());
		
		return result;
	}

	public TXEditingDomain createEditingDomain(ResourceSet rset) {
		// not used when initializing editing domain from extension point
		return null;
	}

	public TXEditingDomain getEditingDomain(ResourceSet rset) {
		// not used when initializing editing domain from extension point
		return null;
	}

}
