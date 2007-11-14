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
 * $Id: BookTitleConstraint.java,v 1.3 2007/11/14 18:14:13 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests.constraints;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.emf.validation.AbstractModelConstraint;
import org.eclipse.emf.validation.EMFEventType;
import org.eclipse.emf.validation.IValidationContext;

/**
 * Constraint used for testing transaction validation scenarios.
 * Requires books to have non-<code>null</code>, non-empty titles.
 *
 * @author Christian W. Damus (cdamus)
 */
public class BookTitleConstraint
	extends AbstractModelConstraint {

	@Override
	public IStatus validate(IValidationContext ctx) {
		EMFEventType eType = ctx.getEventType();
		
		if (eType != EMFEventType.NULL) {
			Object newValue = ctx.getFeatureNewValue();
			
			if (newValue == null
				|| ((String)newValue).length() == 0) {
				return ctx.createFailureStatus();
			}
		}
		
		return ctx.createSuccessStatus();
	}
}
