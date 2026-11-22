export type PaymentStatus =
    | 'CREATED'
    | 'PROCESSING'
    | 'SUCCEEDED'
    | 'FAILED'

export interface Payment {
    id: string
    merchantId: string
    merchantName: string
    amount: number
    currency: string
    status: PaymentStatus
    providerReference: string | null
    createdAt: string
    updatedAt: string
}