/**
 * <copyright>
 *
 * Copyright (c) 2005, 2007 IBM Corporation and others.
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
 * $Id: LibraryDefaultNameTrigger.java,v 1.5 2007/11/14 18:13:54 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests.fixtures;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.examples.extlibrary.Library;
import org.eclipse.emf.transaction.NotificationFilter;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.TriggerListener;

/**
 * A trigger listener that sets a default name on new libraries.
 *
 * @author Christian W. Damus (cdamus)
 */
public class LibraryDefaultNameTrigger extends TriggerListener {
	private final boolean isAggregate;
	
	public LibraryDefaultNameTrigger() {
		this(false);
	}
	
	public LibraryDefaultNameTrigger(boolean isAggregate) {
		super(NotificationFilter.createFeatureFilter(
					EXTLibraryPackage.eINSTANCE.getLibrary_Branches()).and(
							NotificationFilter.createEventTypeFilter(
									Notification.ADD)));
		this.isAggregate = isAggregate;
	}
	
	@Override
	protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
		Command result = null;
		
		Library newLibrary = (Library) notification.getNewValue();
		if ((newLibrary.getName() == null) || (newLibrary.getName().length() == 0)) {
			result= new SetCommand(
					domain,
					newLibrary,
					EXTLibraryPackage.eINSTANCE.getLibrary_Name(),
					"New Library"); //$NON-NLS-1$
		}
		
		return result;
	}
	
	@Override
	public boolean isAggregatePrecommitListener() {
		return isAggregate;
	}
}
