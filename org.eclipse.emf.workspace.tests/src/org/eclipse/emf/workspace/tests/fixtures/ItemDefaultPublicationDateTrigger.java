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
 * $Id: ItemDefaultPublicationDateTrigger.java,v 1.2 2006/01/30 19:47:57 cdamus Exp $
 */
package org.eclipse.emf.workspace.tests.fixtures;

import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.emf.examples.extlibrary.EXTLibraryPackage;
import org.eclipse.emf.examples.extlibrary.Item;
import org.eclipse.emf.transaction.NotificationFilter;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.TriggerListener;

/**
 * A trigger listener that sets a default publication date on new items.
 *
 * @author Christian W. Damus (cdamus)
 */
public class ItemDefaultPublicationDateTrigger extends TriggerListener {
	public ItemDefaultPublicationDateTrigger() {
		super(NotificationFilter.createFeatureFilter(
					EXTLibraryPackage.eINSTANCE.getLibrary_Stock()).and(
							NotificationFilter.createEventTypeFilter(
									Notification.ADD)));
	}
	
	protected Command trigger(TransactionalEditingDomain domain, Notification notification) {
		Command result = null;
		
		Item newItem = (Item) notification.getNewValue();
		if (newItem.getPublicationDate() == null) {
			result= new SetCommand(
					domain,
					newItem,
					EXTLibraryPackage.eINSTANCE.getItem_PublicationDate(),
					new java.util.Date());
		}
		
		return result;
	}
}
