import type { Payment } from '../types/payment'
import PaymentStatusBadge from './PaymentStatusBadge'

interface PaymentListProps {
    payments: Payment[]
    onSelectPayment: (payment: Payment) => void
}

function PaymentList({
                         payments,
                         onSelectPayment,
                     }: PaymentListProps) {
    if (payments.length === 0) {
        return (
            <section>
                <h2>Payments</h2>
                <div className="empty-state">
                    <h3>No payments found</h3>
                    <p>There are no payment records to display.</p>
                </div>
            </section>
        )
    }

    return (
        <section>
            <h2>Payments</h2>

            <table>
                <thead>
                <tr>
                    <th>Payment ID</th>
                    <th>Merchant ID</th>
                    <th>Amount</th>
                    <th>Status</th>
                    <th>Created At</th>
                </tr>
                </thead>

                <tbody>
                {payments.map((payment) => (
                    <tr key={payment.id}>
                        <td>
                            <button
                                type="button"
                                onClick={() => onSelectPayment(payment)}
                            >
                                {payment.id}
                            </button>
                        </td>

                        <td>{payment.merchantName}</td>

                        <td>
                            {payment.currency}{' '}
                            {payment.amount.toFixed(2)}
                        </td>

                        <td>
                            <PaymentStatusBadge status={payment.status}/>
                        </td>

                        <td>
                            {new Date(payment.createdAt).toLocaleString()}
                        </td>
                    </tr>
                ))}
                </tbody>
            </table>
        </section>
    )
}

export default PaymentList
