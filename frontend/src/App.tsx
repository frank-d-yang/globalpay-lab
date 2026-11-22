import { useEffect, useState } from 'react'
import PaymentList from './components/PaymentList'
import PaymentDetails from './components/PaymentDetails'
import { getPayments } from './services/paymentService'
import type { Payment } from './types/payment'
import './App.css'

const PAGE_SIZE = 10

function App() {
    const [payments, setPayments] = useState<Payment[]>([])
    const [selectedPayment, setSelectedPayment] =
        useState<Payment | null>(null)

    const [page, setPage] = useState(0)
    const [total, setTotal] = useState(0)

    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [retryCount, setRetryCount] = useState(0)

    useEffect(() => {
        let active = true

        async function loadPayments() {
            try {
                const result = await getPayments(page, PAGE_SIZE)

                if (active) {
                    setPayments(result.items)
                    setTotal(result.total)
                }
            } catch {
                if (active) {
                    setError('Failed to load payments.')
                }
            } finally {
                if (active) {
                    setLoading(false)
                }
            }
        }

        void loadPayments()

        return () => {
            active = false
        }
    }, [page, retryCount])

    const totalPages = Math.ceil(total / PAGE_SIZE)

    function changePage(nextPage: number) {
        setPage(nextPage)
        setSelectedPayment(null)
        setPayments([])
        setError(null)
        setLoading(true)
    }

    function retry() {
        setError(null)
        setLoading(true)
        setRetryCount((count) => count + 1)
    }

    return (
        <main>
            <header>
                <h1>GlobalPay Lab</h1>
                <p>Sandbox Payment Operations · Live Sandbox</p>
            </header>

            {loading ? (
                <section>
                    <p>Loading payments...</p>
                </section>
            ) : error ? (
                <section role="alert">
                    <p>{error}</p>
                    <button type="button" onClick={retry}>
                        Retry
                    </button>
                </section>
            ) : (
                <>
                    <PaymentList
                        payments={payments}
                        onSelectPayment={setSelectedPayment}
                    />

                    {total > 0 && (
                        <div className="pagination">
                            <button
                                type="button"
                                disabled={page === 0}
                                onClick={() => changePage(page - 1)}
                            >
                                Previous
                            </button>

                            <span>
                Page {page + 1} of {totalPages}
                                {' · '}
                                {total} payments
              </span>

                            <button
                                type="button"
                                disabled={(page + 1) * PAGE_SIZE >= total}
                                onClick={() => changePage(page + 1)}
                            >
                                Next
                            </button>
                        </div>
                    )}

                    {selectedPayment && (
                        <PaymentDetails payment={selectedPayment} />
                    )}
                </>
            )}
        </main>
    )
}

export default App
