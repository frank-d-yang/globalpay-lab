import type { Payment, PaymentStatus } from '../types/payment'

interface PaymentApiResponse {
    id: string
    merchantId: string
    amount: number
    currency: string
    status: PaymentStatus
    providerReference: string | null
    createdAt: string
    updatedAt: string
}

interface PaymentPageResponse {
    items: PaymentApiResponse[]
    page: number
    size: number
    total: number
}

interface ApiResponse<T> {
    code: number
    message: string
    data: T
}

export interface PaymentPage {
    items: Payment[]
    page: number
    size: number
    total: number
}

export async function getPayments(
    page: number = 0,
    size: number = 20
): Promise<PaymentPage> {

    const response = await fetch(
        `/api/v1/payments?page=${page}&size=${size}`
    )

    if (!response.ok) {
        throw new Error('Failed to fetch payments')
    }

    const result: ApiResponse<PaymentPageResponse> =
        await response.json()

    if (result.code !== 200) {
        throw new Error(result.message)
    }

    return {
        items: result.data.items.map((payment) => ({
            ...payment,
            merchantName: payment.merchantId,
        })),
        page: result.data.page,
        size: result.data.size,
        total: result.data.total,
    }
}
