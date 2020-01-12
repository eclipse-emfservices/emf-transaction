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
package org.eclipse.emf.transaction.tests.fixtures;

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
	
	@Override
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
