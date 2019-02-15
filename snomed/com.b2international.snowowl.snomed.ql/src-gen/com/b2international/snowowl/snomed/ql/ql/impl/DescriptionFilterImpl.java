/**
 * Copyright 2019 B2i Healthcare Pte Ltd, http://b2i.sg
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b2international.snowowl.snomed.ql.ql.impl;

import com.b2international.snowowl.snomed.ql.ql.ActiveTerm;
import com.b2international.snowowl.snomed.ql.ql.DescriptionFilter;
import com.b2international.snowowl.snomed.ql.ql.Descriptiontype;
import com.b2international.snowowl.snomed.ql.ql.QlPackage;
import com.b2international.snowowl.snomed.ql.ql.RegularExpression;
import com.b2international.snowowl.snomed.ql.ql.TermFilter;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.NotificationChain;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.InternalEObject;

import org.eclipse.emf.ecore.impl.ENotificationImpl;
import org.eclipse.emf.ecore.impl.MinimalEObjectImpl;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model object '<em><b>Description Filter</b></em>'.
 * <!-- end-user-doc -->
 * <p>
 * The following features are implemented:
 * </p>
 * <ul>
 *   <li>{@link com.b2international.snowowl.snomed.ql.ql.impl.DescriptionFilterImpl#getTermFilter <em>Term Filter</em>}</li>
 *   <li>{@link com.b2international.snowowl.snomed.ql.ql.impl.DescriptionFilterImpl#getActive <em>Active</em>}</li>
 *   <li>{@link com.b2international.snowowl.snomed.ql.ql.impl.DescriptionFilterImpl#getType <em>Type</em>}</li>
 *   <li>{@link com.b2international.snowowl.snomed.ql.ql.impl.DescriptionFilterImpl#getRegex <em>Regex</em>}</li>
 * </ul>
 *
 * @generated
 */
public class DescriptionFilterImpl extends MinimalEObjectImpl.Container implements DescriptionFilter
{
  /**
   * The cached value of the '{@link #getTermFilter() <em>Term Filter</em>}' containment reference.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @see #getTermFilter()
   * @generated
   * @ordered
   */
  protected TermFilter termFilter;

  /**
   * The cached value of the '{@link #getActive() <em>Active</em>}' containment reference.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @see #getActive()
   * @generated
   * @ordered
   */
  protected ActiveTerm active;

  /**
   * The cached value of the '{@link #getType() <em>Type</em>}' containment reference.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @see #getType()
   * @generated
   * @ordered
   */
  protected Descriptiontype type;

  /**
   * The cached value of the '{@link #getRegex() <em>Regex</em>}' containment reference.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @see #getRegex()
   * @generated
   * @ordered
   */
  protected RegularExpression regex;

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  protected DescriptionFilterImpl()
  {
    super();
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  @Override
  protected EClass eStaticClass()
  {
    return QlPackage.Literals.DESCRIPTION_FILTER;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public TermFilter getTermFilter()
  {
    return termFilter;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public NotificationChain basicSetTermFilter(TermFilter newTermFilter, NotificationChain msgs)
  {
    TermFilter oldTermFilter = termFilter;
    termFilter = newTermFilter;
    if (eNotificationRequired())
    {
      ENotificationImpl notification = new ENotificationImpl(this, Notification.SET, QlPackage.DESCRIPTION_FILTER__TERM_FILTER, oldTermFilter, newTermFilter);
      if (msgs == null) msgs = notification; else msgs.add(notification);
    }
    return msgs;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public void setTermFilter(TermFilter newTermFilter)
  {
    if (newTermFilter != termFilter)
    {
      NotificationChain msgs = null;
      if (termFilter != null)
        msgs = ((InternalEObject)termFilter).eInverseRemove(this, EOPPOSITE_FEATURE_BASE - QlPackage.DESCRIPTION_FILTER__TERM_FILTER, null, msgs);
      if (newTermFilter != null)
        msgs = ((InternalEObject)newTermFilter).eInverseAdd(this, EOPPOSITE_FEATURE_BASE - QlPackage.DESCRIPTION_FILTER__TERM_FILTER, null, msgs);
      msgs = basicSetTermFilter(newTermFilter, msgs);
      if (msgs != null) msgs.dispatch();
    }
    else if (eNotificationRequired())
      eNotify(new ENotificationImpl(this, Notification.SET, QlPackage.DESCRIPTION_FILTER__TERM_FILTER, newTermFilter, newTermFilter));
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public ActiveTerm getActive()
  {
    return active;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public NotificationChain basicSetActive(ActiveTerm newActive, NotificationChain msgs)
  {
    ActiveTerm oldActive = active;
    active = newActive;
    if (eNotificationRequired())
    {
      ENotificationImpl notification = new ENotificationImpl(this, Notification.SET, QlPackage.DESCRIPTION_FILTER__ACTIVE, oldActive, newActive);
      if (msgs == null) msgs = notification; else msgs.add(notification);
    }
    return msgs;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public void setActive(ActiveTerm newActive)
  {
    if (newActive != active)
    {
      NotificationChain msgs = null;
      if (active != null)
        msgs = ((InternalEObject)active).eInverseRemove(this, EOPPOSITE_FEATURE_BASE - QlPackage.DESCRIPTION_FILTER__ACTIVE, null, msgs);
      if (newActive != null)
        msgs = ((InternalEObject)newActive).eInverseAdd(this, EOPPOSITE_FEATURE_BASE - QlPackage.DESCRIPTION_FILTER__ACTIVE, null, msgs);
      msgs = basicSetActive(newActive, msgs);
      if (msgs != null) msgs.dispatch();
    }
    else if (eNotificationRequired())
      eNotify(new ENotificationImpl(this, Notification.SET, QlPackage.DESCRIPTION_FILTER__ACTIVE, newActive, newActive));
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public Descriptiontype getType()
  {
    return type;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public NotificationChain basicSetType(Descriptiontype newType, NotificationChain msgs)
  {
    Descriptiontype oldType = type;
    type = newType;
    if (eNotificationRequired())
    {
      ENotificationImpl notification = new ENotificationImpl(this, Notification.SET, QlPackage.DESCRIPTION_FILTER__TYPE, oldType, newType);
      if (msgs == null) msgs = notification; else msgs.add(notification);
    }
    return msgs;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public void setType(Descriptiontype newType)
  {
    if (newType != type)
    {
      NotificationChain msgs = null;
      if (type != null)
        msgs = ((InternalEObject)type).eInverseRemove(this, EOPPOSITE_FEATURE_BASE - QlPackage.DESCRIPTION_FILTER__TYPE, null, msgs);
      if (newType != null)
        msgs = ((InternalEObject)newType).eInverseAdd(this, EOPPOSITE_FEATURE_BASE - QlPackage.DESCRIPTION_FILTER__TYPE, null, msgs);
      msgs = basicSetType(newType, msgs);
      if (msgs != null) msgs.dispatch();
    }
    else if (eNotificationRequired())
      eNotify(new ENotificationImpl(this, Notification.SET, QlPackage.DESCRIPTION_FILTER__TYPE, newType, newType));
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public RegularExpression getRegex()
  {
    return regex;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public NotificationChain basicSetRegex(RegularExpression newRegex, NotificationChain msgs)
  {
    RegularExpression oldRegex = regex;
    regex = newRegex;
    if (eNotificationRequired())
    {
      ENotificationImpl notification = new ENotificationImpl(this, Notification.SET, QlPackage.DESCRIPTION_FILTER__REGEX, oldRegex, newRegex);
      if (msgs == null) msgs = notification; else msgs.add(notification);
    }
    return msgs;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public void setRegex(RegularExpression newRegex)
  {
    if (newRegex != regex)
    {
      NotificationChain msgs = null;
      if (regex != null)
        msgs = ((InternalEObject)regex).eInverseRemove(this, EOPPOSITE_FEATURE_BASE - QlPackage.DESCRIPTION_FILTER__REGEX, null, msgs);
      if (newRegex != null)
        msgs = ((InternalEObject)newRegex).eInverseAdd(this, EOPPOSITE_FEATURE_BASE - QlPackage.DESCRIPTION_FILTER__REGEX, null, msgs);
      msgs = basicSetRegex(newRegex, msgs);
      if (msgs != null) msgs.dispatch();
    }
    else if (eNotificationRequired())
      eNotify(new ENotificationImpl(this, Notification.SET, QlPackage.DESCRIPTION_FILTER__REGEX, newRegex, newRegex));
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  @Override
  public NotificationChain eInverseRemove(InternalEObject otherEnd, int featureID, NotificationChain msgs)
  {
    switch (featureID)
    {
      case QlPackage.DESCRIPTION_FILTER__TERM_FILTER:
        return basicSetTermFilter(null, msgs);
      case QlPackage.DESCRIPTION_FILTER__ACTIVE:
        return basicSetActive(null, msgs);
      case QlPackage.DESCRIPTION_FILTER__TYPE:
        return basicSetType(null, msgs);
      case QlPackage.DESCRIPTION_FILTER__REGEX:
        return basicSetRegex(null, msgs);
    }
    return super.eInverseRemove(otherEnd, featureID, msgs);
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  @Override
  public Object eGet(int featureID, boolean resolve, boolean coreType)
  {
    switch (featureID)
    {
      case QlPackage.DESCRIPTION_FILTER__TERM_FILTER:
        return getTermFilter();
      case QlPackage.DESCRIPTION_FILTER__ACTIVE:
        return getActive();
      case QlPackage.DESCRIPTION_FILTER__TYPE:
        return getType();
      case QlPackage.DESCRIPTION_FILTER__REGEX:
        return getRegex();
    }
    return super.eGet(featureID, resolve, coreType);
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  @Override
  public void eSet(int featureID, Object newValue)
  {
    switch (featureID)
    {
      case QlPackage.DESCRIPTION_FILTER__TERM_FILTER:
        setTermFilter((TermFilter)newValue);
        return;
      case QlPackage.DESCRIPTION_FILTER__ACTIVE:
        setActive((ActiveTerm)newValue);
        return;
      case QlPackage.DESCRIPTION_FILTER__TYPE:
        setType((Descriptiontype)newValue);
        return;
      case QlPackage.DESCRIPTION_FILTER__REGEX:
        setRegex((RegularExpression)newValue);
        return;
    }
    super.eSet(featureID, newValue);
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  @Override
  public void eUnset(int featureID)
  {
    switch (featureID)
    {
      case QlPackage.DESCRIPTION_FILTER__TERM_FILTER:
        setTermFilter((TermFilter)null);
        return;
      case QlPackage.DESCRIPTION_FILTER__ACTIVE:
        setActive((ActiveTerm)null);
        return;
      case QlPackage.DESCRIPTION_FILTER__TYPE:
        setType((Descriptiontype)null);
        return;
      case QlPackage.DESCRIPTION_FILTER__REGEX:
        setRegex((RegularExpression)null);
        return;
    }
    super.eUnset(featureID);
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  @Override
  public boolean eIsSet(int featureID)
  {
    switch (featureID)
    {
      case QlPackage.DESCRIPTION_FILTER__TERM_FILTER:
        return termFilter != null;
      case QlPackage.DESCRIPTION_FILTER__ACTIVE:
        return active != null;
      case QlPackage.DESCRIPTION_FILTER__TYPE:
        return type != null;
      case QlPackage.DESCRIPTION_FILTER__REGEX:
        return regex != null;
    }
    return super.eIsSet(featureID);
  }

} //DescriptionFilterImpl
