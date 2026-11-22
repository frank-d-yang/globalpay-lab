import type { Payment } from '../types/payment'
import PaymentStatusBadge from './PaymentStatusBadge'

interface PaymentDetailsProps {
    payment: Payment
}

function PaymentDetails({ payment }: PaymentDetailsProps) {
    return (
        <section>
            <h2>Payment Details</h2>

            <dl>
                <dt>Payment ID</dt>
                <dd>{payment.id}</dd>

                <dt>Merchant ID</dt>
                <dd>{payment.merchantName}</dd>

                <dt>Amount</dt>
                <dd>
                    {payment.currency} {payment.amount.toFixed(2)}
                </dd>

                <dt>Status</dt>
                <dd>
                    <PaymentStatusBadge status={payment.status}/>
                </dd>

                <dt>Provider Reference</dt>
                <dd>{payment.providerReference ?? 'Not available'}</dd>

                <dt>Created At</dt>
                <dd>{new Date(payment.createdAt).toLocaleString()}</dd>

                <dt>Updated At</dt>
                <dd>{new Date(payment.updatedAt).toLocaleString()}</dd>
            </dl>
        </section>
    )
}

export default PaymentDetails
