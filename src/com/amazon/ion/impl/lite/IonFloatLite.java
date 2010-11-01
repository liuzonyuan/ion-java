// Copyright (c) 2010 Amazon.com, Inc.  All rights reserved.

package com.amazon.ion.impl.lite;

import com.amazon.ion.IonFloat;
import com.amazon.ion.IonType;
import com.amazon.ion.NullValueException;
import com.amazon.ion.ValueVisitor;
import java.math.BigDecimal;

/**
 *
 */
public class IonFloatLite
    extends IonValueLite
    implements IonFloat
{
    // not needed: static private final int SIZE_OF_IEEE_754_64_BITS = 8;

    private static final int HASH_SIGNATURE =
        IonType.FLOAT.toString().hashCode();

    private Double _float_value;

    /**
     * Constructs a <code>null.float</code> element.
     */
    public IonFloatLite(IonSystemLite system, boolean isNull)
    {
        super(system, isNull);
    }

    /**
     * makes a copy of this IonFloat including a copy
     * of the double value which is "naturally" immutable.
     * This calls IonValueImpl to copy the annotations and the
     * field name if appropriate.  The symbol table is not
     * copied as the value is fully materialized and the symbol
     * table is unnecessary.
     */
    @Override
    public IonFloatLite clone()
    {
        IonFloatLite clone = new IonFloatLite(this._context.getSystemLite(), false);

        clone.copyValueContentFrom(this);
        clone.setValue(this._float_value);

        return clone;
    }

    /**
     * Calculate Ion Float hash code as bits of double value,
     * folded on themselves to form a 32 bit value.
     * @return hash code
     */
    @Override
    public int hashCode()
    {
        int hash = HASH_SIGNATURE;
        if (!isNullValue())  {
            long bits = Double.doubleToLongBits(doubleValue());
            hash ^= (int) ((bits >>> 32) ^ bits);
        }
        return hash;
    }

    @Override
    public IonType getType()
    {
        return IonType.FLOAT;
    }


    public float floatValue()
        throws NullValueException
    {
        validateThisNotNull();        return _float_value.floatValue();
    }

    public double doubleValue()
        throws NullValueException
    {
        validateThisNotNull();        return _float_value.doubleValue();
    }

    @Deprecated
    public BigDecimal toBigDecimal()
        throws NullValueException
    {
        return bigDecimalValue();
    }

    public BigDecimal bigDecimalValue()
        throws NullValueException
    {
        if (isNullValue()) {
            return null;
        }
        return new BigDecimal(_float_value.doubleValue());
    }

    public void setValue(float value)
    {
        // base set value will check for the lock
        setValue(new Double(value));
    }

    public void setValue(double value)
    {
        // base setValue will check for the lock
        setValue(new Double(value));
    }

    public void setValue(BigDecimal value)
    {
        checkForLock();
        if (value == null)
        {
            _float_value = null;
            _isNullValue(true);
        }
        else
        {
            setValue(value.doubleValue());
        }
    }

    public void setValue(Double d)
    {
        checkForLock();
        _float_value = d;
        _isNullValue(d == null);
    }

    @Override
    public void accept(ValueVisitor visitor) throws Exception
    {
        visitor.visit(this);
    }
}