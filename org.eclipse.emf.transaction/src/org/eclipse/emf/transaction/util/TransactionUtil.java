/**
 * <copyright>
 *
 * Copyright (c) 2006 IBM Corporation and others.
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
 * $Id: TransactionUtil.java,v 1.3 2006/04/21 18:03:38 cdamus Exp $
 */
package org.eclipse.emf.transaction.util;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.edit.domain.EditingDomain;
import org.eclipse.emf.edit.domain.IEditingDomainProvider;
import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.TransactionalEditingDomain;

/**
 * Static utilities for dealing with EMF elements and resources in a
 * transactional editing domain.
 *
 * @author Christian W. Damus (cdamus)
 */
public class TransactionUtil {
	/** Cannot instantiate. */
	private TransactionUtil() {
		super();
	}
	
	/**
	 * Obtains the transactional editing domain that manages the specified
	 * element.
	 * 
	 * @param eObject an EMF model element
	 * 
	 * @return its editing domain, or <code>null</code> if it is not attached
	 *     to any resource in an editing domain
	 */
	public static TransactionalEditingDomain getEditingDomain(EObject eObject) {
		TransactionalEditingDomain result = null;
		Resource res = eObject.eResource();
		
		if (res != null) {
			result = getEditingDomain(res);
		}
		
		return result;
	}
	
	/**
	 * Obtains the transactional editing domain that manages the specified
	 * resource.
	 * 
	 * @param resource a resource
	 * 
	 * @return its editing domain, or <code>null</code> if it is not in a
	 *     resource set managed by an editing domain
	 */
	public static TransactionalEditingDomain getEditingDomain(Resource resource) {
		TransactionalEditingDomain result = null;
		ResourceSet rset = resource.getResourceSet();
		
		if (rset != null) {
			result = getEditingDomain(rset);
		}
		
		return result;
	}
	
	/**
	 * Obtains the transactional editing domain that manages the specified
	 * resource set.
	 * 
	 * @param rset a resource set
	 * 
	 * @return its editing domain, or <code>null</code> if it is managed by
	 *     an editing domain
	 */
	public static TransactionalEditingDomain getEditingDomain(ResourceSet rset) {
		return TransactionalEditingDomain.Factory.INSTANCE.getEditingDomain(rset);
	}
	
	/**
	 * Obtains the transactional editing domain that manages the specified
	 * object.  This is useful, for example, to get the editing domain from
	 * which a {@link org.eclipse.emf.common.notify.Notification} originated by
	 * testing its
	 * {@link org.eclipse.emf.common.notify.Notification#getNotifier() notifier}.
	 * 
	 * @param object some object
	 * 
	 * @return its editing domain, or <code>null</code> if it is not of a type
	 *     that is managed by an editing domain or if it is of an appropriate
	 *     type but happens not to be managed by an editing domain
	 */
	public static TransactionalEditingDomain getEditingDomain(Object object) {
		TransactionalEditingDomain result = null;
		
		if (object instanceof EObject) {
			result = getEditingDomain((EObject) object);
		} else if (object instanceof Resource) {
			result = getEditingDomain((Resource) object);
		} else if (object instanceof ResourceSet) {
			result = getEditingDomain((ResourceSet) object);
		} else if (object instanceof Transaction) {
			result = ((Transaction) object).getEditingDomain();
		} else if (object instanceof TransactionalEditingDomain) {
			result = (TransactionalEditingDomain) object;
		} else if (object instanceof IEditingDomainProvider) {
			EditingDomain domain = ((IEditingDomainProvider) object).getEditingDomain();
			
			if (domain instanceof TransactionalEditingDomain) {
				result = (TransactionalEditingDomain) domain;
			}
		}
		
		return result;
	}
}
