import type { PaymentStatus } from '../types/payment'

interface PaymentStatusBadgeProps {
    status: PaymentStatus
}

function PaymentStatusBadge({
                                status,
                            }: PaymentStatusBadgeProps) {
    return (
        <span className={`payment-status payment-status--${status.toLowerCase()}`}>
      {status}
    </span>
    )
}

export default PaymentStatusBadge
